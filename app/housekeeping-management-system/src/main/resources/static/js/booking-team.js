(() => {
  const form = document.querySelector("[data-team-booking-form]");
  if (!form) return;

  const serviceInputs = [...form.querySelectorAll('input[name="serviceIds"]')];
  const house = form.querySelector("#teamHouseId");
  const dateInput = form.querySelector("#teamWorkDate");
  const addDateButton = form.querySelector("[data-add-team-date]");
  const daysNode = form.querySelector("[data-team-days]");
  const statusNode = form.querySelector("[data-team-calendar-status]");
  const dateSuggestions = form.querySelector("[data-team-available-dates]");
  const priceLines = form.querySelector("[data-team-price-lines]");
  const durationNode = form.querySelector("[data-team-duration]");
  const totalNode = form.querySelector("[data-team-total]");
  const errorNode = form.querySelector("[data-team-error]");
  const submit = form.querySelector("[data-team-submit]");
  const staffSource = form.querySelector("[data-team-staff-source]");
  const rescheduleBookingId = form.dataset.rescheduleBookingId || "";
  const excludedBookingQueryId = form.dataset.excludeBookingId || "";
  const rescheduleStaffIds = (window.bookingRescheduleStaffIds || []).map(String);
  const rescheduleLeadId = form.dataset.rescheduleLeadId || "";
  const days = new Map();
  const currency = new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND", maximumFractionDigits: 0 });
  let datesRequest = 0;
  const favoriteStaffIds = new Set([...(staffSource?.options || [])]
    .filter(option => option.dataset.favorite === "true")
    .map(option => String(option.value)));

  const isFavorite = person => favoriteStaffIds.has(String(person.id));
  const favoritesFirst = people => [...people].sort((a, b) => Number(isFavorite(b)) - Number(isFavorite(a)));
  const selectedServices = () => serviceInputs.filter(input => input.checked);
  const selectedServiceIds = () => selectedServices().map(input => input.value);
  const basePrice = () => selectedServices().reduce((sum, input) => sum + Number(input.dataset.price || 0), 0);
  const extraPrice = () => selectedServices().reduce((sum, input) => sum + Number(input.dataset.extraPrice || 0), 0);
  const duration = () => selectedServices().reduce((sum, input) => sum + Number(input.dataset.duration || 0), 0);
  const endTime = (start, minutes) => {
    const [hour, minute] = start.split(":").map(Number);
    const total = hour * 60 + minute + minutes;
    return `${String(Math.floor(total / 60)).padStart(2, "0")}:${String(total % 60).padStart(2, "0")}`;
  };
  const dayLabel = value => new Date(`${value}T12:00:00`).toLocaleDateString("vi-VN", { weekday: "long", day: "2-digit", month: "2-digit", year: "numeric" });
  const queryBase = () => {
    const params = new URLSearchParams();
    selectedServiceIds().forEach(id => params.append("serviceIds", id));
    if (house.value) params.set("houseId", house.value);
    if (excludedBookingQueryId) params.set("excludeBookingId", excludedBookingQueryId);
    if (rescheduleBookingId) params.set("durationMinutes", form.dataset.rescheduleDuration || "");
    return params;
  };

  function makeHidden(name, value) {
    const input = document.createElement("input");
    input.type = "hidden";
    input.name = name;
    input.value = value;
    return input;
  }

  function renderPrices() {
    const services = selectedServices();
    priceLines.replaceChildren();
    if (!services.length) priceLines.append(Object.assign(document.createElement("li"), { className: "small text-secondary", textContent: "Chưa chọn dịch vụ" }));
    services.forEach(service => {
      const line = document.createElement("li");
      line.className = "small d-flex justify-content-between gap-2 mb-1";
      const name = document.createElement("span"); name.textContent = service.dataset.name || "Dịch vụ";
      const price = document.createElement("strong"); price.textContent = currency.format(Number(service.dataset.price || 0));
      line.append(name, price); priceLines.append(line);
    });
    durationNode.textContent = `${duration()} phút`;
    const total = [...days.values()].reduce((sum, day) => {
      const count = day.staffIds.length;
      return sum + (day.time && count ? basePrice() + Math.max(0, count - 1) * extraPrice() : 0);
    }, 0);
    totalNode.textContent = currency.format(total);
  }

  async function refreshAvailableDates() {
    const request = ++datesRequest;
    dateSuggestions.replaceChildren();
    if (!house.value || !selectedServices().length) {
      statusNode.textContent = "Chọn dịch vụ và địa chỉ trước khi tra ngày còn lịch.";
      return;
    }
    statusNode.textContent = "Đang kiểm tra lịch của nhân viên…";
    try {
      const response = await fetch(`/customer/bookings/availability-dates?${queryBase()}`, { headers: { Accept: "application/json" } });
      const result = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(result.detail || "Không tải được lịch ngày.");
      if (request !== datesRequest) return;
      statusNode.textContent = result.message || "Chọn một ngày phù hợp.";
      (result.dates || []).forEach(date => {
        const button = document.createElement("button");
        button.type = "button";
        button.className = `btn btn-sm btn-outline-secondary${date === dateInput.value ? " active" : ""}`;
        button.textContent = dayLabel(date);
        button.setAttribute("aria-pressed", String(date === dateInput.value));
        button.addEventListener("click", () => {
          dateInput.value = date;
          addDay(date, true);
        });
        dateSuggestions.append(button);
      });
    } catch (error) {
      if (request === datesRequest) statusNode.textContent = error.message || "Không kiểm tra được lịch ngày.";
    }
  }

  async function getOptions(params) {
    const response = await fetch(`/customer/bookings/availability-options?${params}`, { headers: { Accept: "application/json" } });
    const result = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(result.detail || "Không tải được giờ khả dụng.");
    return result;
  }

  async function loadDay(day) {
    if (!house.value || !selectedServices().length) {
      day.message = "Chọn dịch vụ và địa chỉ trước khi tra giờ.";
      day.loading = false;
      day.error = "";
      day.timeOptions = [];
      day.eligibleStaff = [];
      day.routeCheckedTime = "";
      render();
      return;
    }

    const request = (day.request || 0) + 1;
    day.request = request;
    day.loading = true;
    day.error = "";
    day.routeCheckedTime = "";
    day.eligibleStaff = [];
    render();

    try {
      const generalParams = queryBase();
      generalParams.set("workDate", day.date);
      const generalResult = await getOptions(generalParams);
      if (day.request !== request) return;
      day.timeOptions = generalResult.timeOptions || [];
      day.message = generalResult.message || "";

      if (day.time && !day.timeOptions.some(option => option.startTime === day.time)) {
        day.time = "";
        day.staffIds = [];
        day.leadStaffId = "";
        day.message = generalResult.message || "Giờ đã chọn không còn lịch chung. Vui lòng chọn giờ khác.";
      }

      if (day.time) {
        const selectedTime = day.time;
        const routeParams = queryBase();
        routeParams.set("workDate", day.date);
        routeParams.set("startTime", selectedTime);
        day.message = "Đang kiểm tra lại tuyến di chuyển cho giờ đã chọn…";
        render();

        const routeResult = await getOptions(routeParams);
        if (day.request !== request) return;
        day.routeCheckedTime = selectedTime;
        day.eligibleStaff = routeResult.timeOptions?.find(option => option.startTime === selectedTime)?.staffOptions || [];
        const eligibleIds = day.eligibleStaff.map(person => String(person.id));
        day.staffIds = day.staffIds.filter(id => eligibleIds.includes(String(id)));
        if (!day.staffIds.includes(String(day.leadStaffId))) day.leadStaffId = day.staffIds[0] || "";
        day.message = routeResult.message || (day.eligibleStaff.length ? "Đã kiểm tra giờ và tuyến di chuyển. Chọn nhóm nhân viên bên dưới." : "Không có nhân viên phù hợp tuyến di chuyển ở giờ này. Vui lòng chọn giờ khác.");
      }
    } catch (error) {
      if (day.request === request) {
        day.error = error.message || "Không tải được giờ.";
        day.eligibleStaff = [];
        day.routeCheckedTime = "";
      }
    } finally {
      if (day.request === request) { day.loading = false; render(); }
    }
  }

  function addDay(date = dateInput.value, keepDateInput = false) {
    if (!date || (days.has(date) && !rescheduleBookingId)) return;
    let preferredStaffIds = [];
    let preferredLeadId = "";
    if (rescheduleBookingId) {
      const current = [...days.values()][0];
      preferredStaffIds = current ? [...current.staffIds] : [...rescheduleStaffIds];
      preferredLeadId = current?.leadStaffId || rescheduleLeadId;
      days.clear();
    }
    if (!selectedServices().length || !house.value) {
      errorNode.textContent = "Chọn ít nhất một dịch vụ và địa chỉ trước.";
      return;
    }
    errorNode.textContent = "";
    const day = { date, time: "", staffIds: preferredStaffIds, leadStaffId: preferredLeadId,
      timeOptions: [], eligibleStaff: [], routeCheckedTime: "", loading: false, error: "", message: "", request: 0 };
    days.set(date, day);
    loadDay(day);
    if (!keepDateInput) dateInput.value = "";
  }

  function renderDay(day) {
    const card = document.createElement("article");
    card.className = "surface p-3";
    card.append(makeHidden("workDates", day.date));
    card.append(makeHidden("staffIds", day.staffIds.join("|")));
    card.append(makeHidden("leadStaffIds", day.leadStaffId));
    card.append(makeHidden("startTimes", day.time));

    const heading = document.createElement("div");
    heading.className = "d-flex justify-content-between align-items-start gap-2";
    const title = document.createElement("strong"); title.textContent = dayLabel(day.date);
    const remove = document.createElement("button");
    remove.type = "button"; remove.className = "btn btn-sm btn-outline-danger"; remove.textContent = "Bỏ ngày";
    remove.addEventListener("click", () => { days.delete(day.date); render(); });
    if (rescheduleBookingId) remove.hidden = true;
    heading.append(title, remove); card.append(heading);

    const info = document.createElement("p");
    info.className = "small text-secondary mt-2 mb-2";
    info.textContent = day.loading ? (day.time ? "Đang kiểm tra giờ và tuyến di chuyển…" : "Đang kiểm tra lịch nhóm theo từng giờ…")
      : day.error || day.message || (day.time ? "Chọn nhân viên trong nhóm giờ này và đánh dấu trưởng nhóm." : "Chọn một khung giờ để xem và chọn nhóm nhân viên cùng rảnh.");
    card.append(info);

    if (day.timeOptions.length) {
      const timeLabel = document.createElement("p");
      timeLabel.className = "form-label fw-semibold mb-2";
      timeLabel.textContent = "Khung giờ và nhân viên cùng rảnh";
      card.append(timeLabel);
      const note = document.createElement("p");
      note.className = "small text-secondary mb-2";
      note.textContent = "Danh sách cho biết ai có lịch trống cùng lúc. Sau khi chọn giờ, hệ thống kiểm tra thêm thời gian di chuyển đến địa chỉ.";
      card.append(note);
      const times = document.createElement("div"); times.className = "team-time-options";
      day.timeOptions.forEach(option => {
        const selected = day.time === option.startTime;
        const people = favoritesFirst(selected && day.routeCheckedTime === option.startTime ? day.eligibleStaff : option.staffOptions);
        const button = document.createElement("button");
        button.type = "button";
        button.className = `team-time-option${selected ? " is-selected" : ""}`;
        button.setAttribute("aria-pressed", String(selected));
        button.addEventListener("click", () => {
          if (day.time === option.startTime && (day.loading || day.routeCheckedTime === day.time)) return;
          day.time = option.startTime;
          day.staffIds = [];
          day.leadStaffId = "";
          day.routeCheckedTime = "";
          day.eligibleStaff = [];
          loadDay(day);
        });
        const top = document.createElement("span"); top.className = "team-time-option-heading";
        const time = document.createElement("strong"); time.textContent = `${option.startTime}–${endTime(option.startTime, duration())}`;
        const capacity = document.createElement("span"); capacity.className = "team-time-capacity";
        capacity.textContent = `${people.length} người`;
        top.append(time, capacity);
        const names = document.createElement("span"); names.className = "team-time-option-members";
        if (people.length) {
          people.forEach(person => {
            const personTag = document.createElement("span"); personTag.className = "team-time-person";
            const name = document.createElement("span"); name.textContent = person.name;
            personTag.append(name);
            if (isFavorite(person)) {
              const favoriteBadge = document.createElement("span"); favoriteBadge.className = "team-favorite-badge";
              favoriteBadge.textContent = "★ Yêu thích";
              personTag.append(favoriteBadge);
            }
            names.append(personTag);
          });
        } else {
          names.textContent = "Không có người phù hợp tuyến di chuyển";
        }
        button.append(top, names);
        times.append(button);
      });
      card.append(times);
    } else if (!day.loading && !day.error && !day.message) {
      const empty = document.createElement("p"); empty.className = "small text-secondary mb-0";
      empty.textContent = "Ngày này hiện chưa có khung giờ khả dụng.";
      card.append(empty);
    }

    if (day.time) {
      const title = document.createElement("p"); title.className = "small fw-semibold mt-3 mb-2";
      title.textContent = day.routeCheckedTime === day.time
        ? `Nhân viên có thể nhận lúc ${day.time}: chọn một hoặc nhiều người và một trưởng nhóm.`
        : `Nhân viên phù hợp lúc ${day.time}`;
      card.append(title);
      if (day.loading || (!day.error && day.routeCheckedTime !== day.time)) {
        const pending = document.createElement("p"); pending.className = "small text-secondary mb-0";
        pending.textContent = "Đang đối chiếu tuyến di chuyển và lịch của từng người…";
        card.append(pending);
      } else if (day.error) {
        const failed = document.createElement("p"); failed.className = "small text-danger mb-0";
        failed.textContent = "Chưa kiểm tra được nhóm ở khung giờ này. Hãy chọn lại khung giờ để thử lại.";
        card.append(failed);
      } else if (!day.eligibleStaff.length) {
        const unavailable = document.createElement("p"); unavailable.className = "small text-warning mb-0";
        unavailable.textContent = "Không có nhân viên phù hợp ở giờ này. Hãy chọn khung giờ khác.";
        card.append(unavailable);
      } else {
        const list = document.createElement("div"); list.className = "team-staff-options";
        favoritesFirst(day.eligibleStaff).forEach(person => {
          const id = String(person.id);
          const row = document.createElement("div"); row.className = "team-staff-option";
          const checkboxLabel = document.createElement("label"); checkboxLabel.className = "team-staff-choice";
          const checkbox = document.createElement("input"); checkbox.type = "checkbox"; checkbox.className = "form-check-input m-0";
          checkbox.checked = day.staffIds.includes(id);
          const name = document.createElement("span"); name.className = "team-staff-name";
          const personName = document.createElement("span"); personName.textContent = person.name; name.append(personName);
          if (isFavorite(person)) {
            const favoriteBadge = document.createElement("span"); favoriteBadge.className = "team-favorite-badge";
            favoriteBadge.textContent = "★ Yêu thích";
            name.append(favoriteBadge);
          }
          checkboxLabel.append(checkbox, name);
          checkbox.addEventListener("change", () => {
            day.staffIds = checkbox.checked ? [...day.staffIds, id] : day.staffIds.filter(item => item !== id);
            if (!day.staffIds.includes(day.leadStaffId)) day.leadStaffId = day.staffIds[0] || "";
            render();
          });

          const leadLabel = document.createElement("label"); leadLabel.className = "team-lead-choice";
          const lead = document.createElement("input"); lead.type = "radio"; lead.name = `lead-${day.date}`; lead.title = "Trưởng nhóm";
          lead.checked = day.leadStaffId === id;
          lead.disabled = !checkbox.checked;
          leadLabel.append(lead, document.createTextNode("Trưởng nhóm"));
          lead.addEventListener("change", () => {
            if (!day.staffIds.includes(id)) day.staffIds = [...day.staffIds, id];
            day.leadStaffId = id;
            render();
          });
          row.append(checkboxLabel, leadLabel); list.append(row);
        });
        card.append(list);
        if (!day.staffIds.length || !day.leadStaffId) {
          const note = document.createElement("p"); note.className = "small text-warning mt-2 mb-0";
          note.textContent = "Chọn ít nhất một nhân viên, sau đó đánh dấu trưởng nhóm.";
          card.append(note);
        }
      }
    }
    return card;
  }

  function render() {
    daysNode.replaceChildren();
    if (!days.size) daysNode.append(Object.assign(document.createElement("p"), { className: "small text-secondary mb-0", textContent: "Chưa thêm ngày nào." }));
    days.forEach(day => daysNode.append(renderDay(day)));
    renderPrices();
    const complete = [...days.values()].length > 0 && [...days.values()].every(day => day.time && day.routeCheckedTime === day.time
      && day.staffIds.length > 0 && day.leadStaffId && day.staffIds.includes(String(day.leadStaffId)) && !day.loading && !day.error);
    submit.disabled = !selectedServices().length || !house.value || !complete;
  }

  serviceInputs.forEach(input => input.addEventListener("change", () => {
    days.forEach(day => { day.time = ""; day.staffIds = []; day.leadStaffId = ""; day.timeOptions = []; loadDay(day); });
    refreshAvailableDates(); render();
  }));
  house.addEventListener("change", () => {
    days.forEach(day => { day.time = ""; day.staffIds = []; day.leadStaffId = ""; day.timeOptions = []; loadDay(day); });
    refreshAvailableDates(); render();
  });
  addDateButton.addEventListener("click", () => addDay());

  form.addEventListener("submit", event => {
    if (rescheduleBookingId && days.size !== 1) {
      event.preventDefault(); errorNode.textContent = "Dời lịch chỉ áp dụng cho một ngày trên booking hiện tại."; return;
    }
    if (submit.disabled) { event.preventDefault(); errorNode.textContent = "Mỗi ngày cần có giờ đã kiểm tra, ít nhất một nhân viên và một trưởng nhóm."; return; }
  });

  const retryState = window.bookingRetryState;
  if (retryState) {
    if (retryState.houseId) house.value = String(retryState.houseId);
    if (Array.isArray(retryState.serviceIds)) {
      const ids = retryState.serviceIds.map(String);
      serviceInputs.forEach(input => { input.checked = ids.includes(input.value); });
    }
    const areaNote = form.querySelector("#teamAreaNote");
    if (areaNote && retryState.areaNote) areaNote.value = retryState.areaNote;
    if (Array.isArray(retryState.days)) retryState.days.forEach(item => {
      const date = String(item.date || "");
      if (!date || days.has(date)) return;
      const rawStaff = item.staffIds ?? (item.staffId == null ? [] : [item.staffId]);
      const staffIds = Array.isArray(rawStaff) ? rawStaff.map(String)
        : String(rawStaff).split("|").map(value => value.trim()).filter(Boolean);
      days.set(date, { date, time: String(item.time || ""), staffIds,
        leadStaffId: String(item.leadStaffId ?? staffIds[0] ?? ""), timeOptions: [], eligibleStaff: [], routeCheckedTime: "",
        loading: false, error: "", message: "", request: 0 });
    });
  }

  if (rescheduleBookingId && !days.size) {
    const date = form.dataset.rescheduleDate || "";
    const time = form.dataset.rescheduleTime || "";
    if (date) {
      dateInput.value = date;
      days.set(date, { date, time, staffIds: [...rescheduleStaffIds], leadStaffId: rescheduleLeadId,
        timeOptions: [], eligibleStaff: [], routeCheckedTime: "", loading: false, error: "", message: "", request: 0 });
    }
  }

  refreshAvailableDates();
  days.forEach(day => loadDay(day));
  render();
})();
