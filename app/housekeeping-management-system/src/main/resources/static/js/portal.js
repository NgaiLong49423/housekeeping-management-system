"use strict";

// Progressive enhancement only. Identity, prices, ownership and business transitions are verified by Spring.
document.querySelectorAll("form[data-confirm]").forEach(form => {
  form.addEventListener("submit", event => {
    if (!window.confirm(form.dataset.confirm)) event.preventDefault();
  });
});

const scheduleFilters = document.querySelector("[data-staff-schedule-filter]");
if (scheduleFilters) {
  const rows = [...document.querySelectorAll("[data-staff-schedule-row]")];
  const buttons = [...scheduleFilters.querySelectorAll("[data-filter-schedule]")];
  const today = new Date();
  const todayKey = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}-${String(today.getDate()).padStart(2, "0")}`;
  const kindOf = row => row.dataset.startDate < todayKey ? "past" : "upcoming";
  ["all", "upcoming", "past"].forEach(kind => {
    const count = scheduleFilters.querySelector(`[data-count="${kind}"]`);
    if (count) count.textContent = rows.filter(row => kind === "all" || kindOf(row) === kind).length;
  });
  const applyFilter = kind => {
    rows.forEach(row => {
      const hidden = kind !== "all" && kindOf(row) !== kind;
      row.hidden = hidden;
      const editRow = row.nextElementSibling;
      if (editRow?.classList.contains("staff-edit-row") && hidden) editRow.hidden = true;
    });
    const empty = document.querySelector("[data-schedule-filter-empty]");
    if (empty) empty.hidden = rows.some(row => !row.hidden);
  };
  buttons.forEach(button => button.addEventListener("click", () => {
    buttons.forEach(item => item.classList.toggle("active", item === button));
    applyFilter(button.dataset.filterSchedule);
  }));
  applyFilter("all");
}

document.querySelectorAll("[data-edit-toggle]").forEach(button => {
  button.addEventListener("click", () => {
    const editRow = document.getElementById(button.dataset.editToggle);
    if (!editRow) return;
    editRow.hidden = !editRow.hidden;
    button.setAttribute("aria-expanded", String(!editRow.hidden));
    if (!editRow.hidden) editRow.querySelector("input")?.focus();
  });
});

document.querySelectorAll("[data-edit-cancel]").forEach(button => {
  button.addEventListener("click", () => {
    const editRow = button.closest(".staff-edit-row");
    if (editRow) editRow.hidden = true;
  });
});

const bookingFilter = document.querySelector("[data-booking-filter]");
if (bookingFilter) bookingFilter.addEventListener("change", () => {
  const rows = [...document.querySelectorAll("[data-booking-row]")];
  rows.forEach(row => { row.hidden = Boolean(bookingFilter.value && row.dataset.status !== bookingFilter.value); });
  const emptyMessage = document.querySelector("[data-filter-empty]");
  if (emptyMessage) emptyMessage.hidden = rows.some(row => !row.hidden);
});

const favoritesFilter = document.querySelector("[data-favorites-only]");
if (favoritesFilter) favoritesFilter.addEventListener("change", () => {
  const cards = [...document.querySelectorAll("[data-staff-card]")];
  cards.forEach(card => { card.hidden = favoritesFilter.checked && card.dataset.favorite !== "true"; });
  document.querySelector("[data-favorites-empty]").hidden = !favoritesFilter.checked || cards.some(card => !card.hidden);
});

// A compact time field opens a scrollable hour/minute picker on demand.
function initQuarterTimePicker(group) {
  if (!group || group.dataset.timePickerReady === "true") return;
  const trigger = group.querySelector("[data-time-trigger]");
  const value = group.querySelector("[data-time-value]");
  if (!trigger || !value) return;
  group.dataset.timePickerReady = "true";
  const date = group.dataset.dateSource ? document.querySelector(group.dataset.dateSource) : null;
  const initial = value.value || "";
  if (date && /^\d{4}-\d{2}-\d{2}T/.test(initial)) date.value = initial.slice(0, 10);
  const initialTime = date ? initial.split("T")[1] : initial;
  let hour = /^\d{2}:\d{2}/.test(initialTime || "") ? initialTime.slice(0, 2) : "";
  let minute = /^\d{2}:\d{2}/.test(initialTime || "") ? initialTime.slice(3, 5) : "";

  const popup = document.createElement("div");
  popup.className = "time-picker-popup";
  popup.hidden = true;
  popup.setAttribute("role", "group");
  popup.setAttribute("aria-label", trigger.getAttribute("aria-label") || "Chọn giờ");
  const makeColumn = (label, values, selected, onChoose) => {
    const column = document.createElement("div");
    column.className = "time-wheel-column";
    const heading = document.createElement("span");
    heading.className = "time-wheel-heading";
    heading.textContent = label;
    const list = document.createElement("div");
    list.className = "time-wheel-list";
    list.tabIndex = 0;
    list.setAttribute("role", "listbox");
    list.setAttribute("aria-label", label);
    values.forEach(item => {
      const option = document.createElement("button");
      option.type = "button";
      option.className = "time-wheel-option";
      option.textContent = item;
      option.dataset.value = item;
      option.setAttribute("role", "option");
      option.setAttribute("aria-selected", String(item === selected));
      option.addEventListener("click", () => onChoose(item));
      list.append(option);
    });
    column.append(heading, list);
    return { column, list };
  };
  const hours = Array.from({ length: 24 }, (_, index) => String(index).padStart(2, "0"));
  const minutes = ["00", "15", "30", "45"];
  const minLeadMinutes = Number(group.dataset.minLeadMinutes || 0);
  let allowedTimes = group.hasAttribute("data-booking-time-picker") ? [] : null;
  let hourList, minuteList;
  let pendingChange = false;
  const earliestAllowedMinute = () => {
    if (!date || !date.value || !minLeadMinutes) return null;
    const now = new Date();
    const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
    if (date.value > today) return null;
    if (date.value < today) return 24 * 60;
    const earliest = new Date(now.getTime() + minLeadMinutes * 60_000);
    return Math.ceil((earliest.getHours() * 60 + earliest.getMinutes()) / 15) * 15;
  };
  const constrainOptions = () => {
    const earliest = earliestAllowedMinute();
    const permitted = time => allowedTimes === null || allowedTimes.includes(time);
    if (hour && minute && !permitted(`${hour}:${minute}`)) { hour = ""; minute = ""; }
    if (earliest != null && hour && minute && Number(hour) * 60 + Number(minute) < earliest) {
      hour = "";
      minute = "";
    }
    hourList?.querySelectorAll("[role=option]").forEach(option => {
      option.hidden = (earliest != null && Number(option.dataset.value) * 60 + 45 < earliest)
        || (allowedTimes !== null && !allowedTimes.some(time => time.startsWith(`${option.dataset.value}:`)));
    });
    minuteList?.querySelectorAll("[role=option]").forEach(option => {
      const candidate = hour ? `${hour}:${option.dataset.value}` : null;
      option.hidden = (earliest != null && hour !== "" && Number(hour) * 60 + Number(option.dataset.value) < earliest)
        || (allowedTimes !== null && (candidate ? !permitted(candidate)
          : !allowedTimes.some(time => time.endsWith(`:${option.dataset.value}`))));
    });
  };
  const render = (list, selected) => {
    list.querySelectorAll("[role=option]").forEach(option => {
      const active = option.dataset.value === selected;
      option.setAttribute("aria-selected", String(active));
      if (active && !popup.hidden) {
        const listBounds = list.getBoundingClientRect();
        const optionBounds = option.getBoundingClientRect();
        list.scrollTop += optionBounds.top - listBounds.top - (list.clientHeight - optionBounds.height) / 2;
      }
    });
  };
  const sync = notify => {
    constrainOptions();
    value.value = hour && minute
      ? (date ? (date.value ? `${date.value}T${hour}:${minute}` : "") : `${hour}:${minute}`)
      : "";
    trigger.value = hour && minute ? `${hour}:${minute}` : "";
    if (hourList) render(hourList, hour);
    if (minuteList) render(minuteList, minute);
    if (notify) {
      if (group.hasAttribute("data-change-on-done")) pendingChange = true;
      else trigger.dispatchEvent(new Event("change", { bubbles: true }));
    }
  };
  group.setTime = value => {
    if (!/^\d{2}:\d{2}$/.test(value || "")) return;
    hour = value.slice(0, 2);
    minute = value.slice(3, 5);
    sync(true);
  };
  group.setAllowedTimes = times => {
    allowedTimes = Array.isArray(times) ? times : [];
    constrainOptions();
    sync(false);
  };
  group.clearTime = () => { hour = ""; minute = ""; sync(false); };
  const hourColumn = makeColumn("Giờ", hours, hour, item => { hour = item; sync(true); });
  const minuteColumn = makeColumn("Phút", minutes, minute, item => { minute = item; sync(true); });
  hourList = hourColumn.list;
  minuteList = minuteColumn.list;
  const done = document.createElement("button");
  done.type = "button";
  done.className = "btn btn-primary btn-sm time-picker-done";
  done.textContent = "Xong";
  done.addEventListener("click", () => {
    popup.hidden = true;
    trigger.setAttribute("aria-expanded", "false");
    if (pendingChange && hour && minute) {
      pendingChange = false;
      popup.remove();
      trigger.dispatchEvent(new Event("change", { bubbles: true }));
    } else {
      pendingChange = false;
      trigger.focus();
    }
  });
  popup.append(hourColumn.column, minuteColumn.column, done);
  document.body.append(popup);
  const positionPopup = () => {
    const rect = trigger.getBoundingClientRect();
    const popupHeight = popup.offsetHeight;
    const top = rect.bottom + popupHeight + 8 <= window.innerHeight ? rect.bottom + 8 : Math.max(8, rect.top - popupHeight - 8);
    popup.style.top = `${top}px`;
    popup.style.left = `${Math.min(rect.left, window.innerWidth - popup.offsetWidth - 8)}px`;
  };
  trigger.setAttribute("aria-haspopup", "true");
  trigger.setAttribute("aria-expanded", "false");
  trigger.addEventListener("click", () => {
    popup.hidden = !popup.hidden;
    trigger.setAttribute("aria-expanded", String(!popup.hidden));
    if (!popup.hidden) { constrainOptions(); positionPopup(); render(hourList, hour); render(minuteList, minute); }
  });
  trigger.addEventListener("keydown", event => {
    if (event.key === "Escape" && !popup.hidden) {
      popup.hidden = true;
      trigger.setAttribute("aria-expanded", "false");
    }
  });
  document.addEventListener("click", event => {
    if (!group.contains(event.target) && !popup.contains(event.target) && !popup.hidden) {
      popup.hidden = true;
      trigger.setAttribute("aria-expanded", "false");
    }
  });
  window.addEventListener("resize", () => { if (!popup.hidden) positionPopup(); });
  window.addEventListener("scroll", () => { if (!popup.hidden) positionPopup(); }, true);
  date?.addEventListener("change", () => sync(true));
  sync(false);
}
window.initQuarterTimePicker = initQuarterTimePicker;
document.querySelectorAll("[data-quarter-time]").forEach(initQuarterTimePicker);

const companyWindowSource = document.querySelector("#company-operating-hours-source");
if (companyWindowSource) {
  const configuredWindows = [...companyWindowSource.querySelectorAll("[data-company-window]")];
  document.querySelectorAll("[data-company-window-batch-form]").forEach(form => {
    const startDate = form.querySelector("[data-batch-start]");
    const endDate = form.querySelector("[data-batch-end]");
    const weekdays = [...form.querySelectorAll("[data-batch-weekday]")];
    const list = form.querySelector("[data-batch-window-list]");
    const message = form.querySelector("[data-batch-window-message]");
    const submit = form.querySelector("[type=submit]");
    const windowsFor = (dateValue, day) => {
      const version = configuredWindows.map(item => item.dataset.effectiveFrom).filter(value => value <= dateValue).sort().at(-1);
      return configuredWindows.filter(item => item.dataset.effectiveFrom === version && Number(item.dataset.day) === day && item.dataset.closed !== "true")
        .map(item => ({ start: item.dataset.start, end: item.dataset.end }));
    };
    const validate = () => {
      list.replaceChildren();
      const selectedDays = weekdays.filter(input => input.checked).map(input => Number(input.value));
      let error = "";
      if (!startDate.value || !endDate.value || endDate.value < startDate.value) error = "Hãy chọn khoảng ngày hợp lệ.";
      else if (!selectedDays.length) error = "Hãy chọn ít nhất một thứ làm việc.";
      const dates = [];
      if (!error) {
        const start = new Date(`${startDate.value}T00:00:00Z`);
        const end = new Date(`${endDate.value}T00:00:00Z`);
        for (let date = new Date(start); date <= end; date.setUTCDate(date.getUTCDate() + 1)) {
          const day = (date.getUTCDay() + 6) % 7 + 1;
          if (selectedDays.includes(day)) dates.push({ value: date.toISOString().slice(0, 10), day });
        }
        if (!dates.length) error = "Khoảng ngày không có thứ nào bạn đã chọn.";
      }
      let invalidWindow = false;
      dates.forEach(({ value, day }) => {
        const windows = windowsFor(value, day);
        if (!windows.length) invalidWindow = true;
        const item = document.createElement("span");
        item.className = "staff-company-window-chip";
        const dayName = ["", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy", "Chủ nhật"][day];
        item.textContent = `${dayName} ${value}: ${windows.length ? windows.map(window => `${window.start}–${window.end}`).join(", ") : "Nghỉ"}`;
        list.append(item);
      });
      if (!error && invalidWindow) error = "Có ngày Admin không mở cửa trong khoảng đã chọn; hãy bỏ thứ đó hoặc thu hẹp khoảng ngày.";
      const start = (form.elements.startTime?.value || "").slice(0, 5);
      const end = (form.elements.endTime?.value || "").slice(0, 5);
      if (!error && (!start || !end)) error = "Hãy chọn giờ bắt đầu và giờ kết thúc.";
      else if (!error && start >= end) error = "Giờ kết thúc phải sau giờ bắt đầu.";
      else if (!error && dates.some(({ value, day }) => !windowsFor(value, day).some(window => start >= window.start && end <= window.end))) {
        error = "Khoảng giờ phải nằm trọn trong giờ Admin cho tất cả ngày được chọn.";
      }
      message.textContent = error;
      message.hidden = !error;
      submit.disabled = Boolean(error);
      return !error;
    };
    [startDate, endDate, ...weekdays].forEach(input => input.addEventListener("change", validate));
    form.querySelectorAll("[data-time-trigger]").forEach(trigger => trigger.addEventListener("change", validate));
    form.addEventListener("submit", event => { if (!validate()) event.preventDefault(); });
    validate();
  });
  document.querySelectorAll("[data-company-window-form]").forEach(form => {
    const dateInput = form.querySelector("[data-work-date]");
    const list = form.querySelector("[data-company-window-list]");
    const message = form.querySelector("[data-company-window-message]");
    const submit = form.querySelector("[type=submit]");
    const windowForDate = () => {
      if (!dateInput.value) return [];
      const date = new Date(`${dateInput.value}T12:00:00`);
      const dayOfWeek = (date.getDay() + 6) % 7 + 1;
      const version = configuredWindows.map(item => item.dataset.effectiveFrom).filter(value => value <= dateInput.value).sort().at(-1);
      return configuredWindows
        .filter(item => item.dataset.effectiveFrom === version && Number(item.dataset.day) === dayOfWeek && item.dataset.closed !== "true")
        .map(item => ({ start: item.dataset.start, end: item.dataset.end }));
    };
    const validate = () => {
      const windows = windowForDate();
      if (list) {
        list.replaceChildren();
        if (!dateInput.value) {
          list.textContent = "Chọn ngày để xem khung giờ Admin đã cấu hình.";
        } else if (!windows.length) {
          list.textContent = "Ngày này Admin đang cấu hình nghỉ.";
        } else {
          windows.forEach(window => {
            const chip = document.createElement("span");
            chip.className = "staff-company-window-chip";
            chip.textContent = `${window.start} – ${window.end}`;
            list.append(chip);
          });
        }
      }
      let error = "";
      if (!dateInput.value) error = "Hãy chọn ngày làm.";
      else if (form.dataset.today && dateInput.value < form.dataset.today) error = "Ngày đã qua chỉ có thể xem, không được chỉnh hoặc đăng ký.";
      else if (!windows.length) error = "Ngày này không có khung giờ hoạt động của công ty.";
      const intervals = form.hasAttribute("data-staff-multi-form")
        ? [...form.querySelectorAll("[data-staff-interval-row]")].map(row => ({
          start: (row.querySelector('[name="startTimes"]')?.value || "").slice(0, 5),
          end: (row.querySelector('[name="endTimes"]')?.value || "").slice(0, 5)
        }))
        : [{ start: (form.elements.startTime?.value || "").slice(0, 5), end: (form.elements.endTime?.value || "").slice(0, 5) }];
      if (!error && intervals.some(interval => !interval.start || !interval.end)) error = "Hãy chọn giờ bắt đầu và giờ kết thúc cho từng khoảng.";
      else if (!error && intervals.some(interval => interval.start >= interval.end)) error = "Giờ kết thúc phải sau giờ bắt đầu ở mọi khoảng.";
      else if (!error && intervals.some(interval => !windows.some(window => interval.start >= window.start && interval.end <= window.end))) {
        error = "Mỗi khoảng sẵn sàng phải nằm trọn trong một khung giờ Admin đã cấu hình.";
      }
      if (!error) {
        const sorted = [...intervals].sort((left, right) => left.start.localeCompare(right.start));
        if (sorted.some((interval, index) => index > 0 && interval.start < sorted[index - 1].end)) {
          error = "Các khoảng giờ bạn chọn đang chồng lấn nhau.";
        }
      }
      if (!error && form.dataset.today && dateInput.value === form.dataset.today) {
        const now = new Date();
        const earliest = new Date(now.getTime() + 120 * 60_000);
        const todayKey = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
        const minimumDuration = Number(form.dataset.minServiceDuration || 0);
        const earliestMinute = Math.ceil((earliest.getHours() * 60 + earliest.getMinutes()) / 15) * 15;
        if (todayKey !== dateInput.value || earliest.toDateString() !== now.toDateString()
            || !intervals.every(interval => {
              const startMinute = Number(interval.start.slice(0, 2)) * 60 + Number(interval.start.slice(3, 5));
              const endMinute = Number(interval.end.slice(0, 2)) * 60 + Number(interval.end.slice(3, 5));
              return windows.some(window => {
                const candidate = Math.max(startMinute, earliestMinute, Number(window.start.slice(0, 2)) * 60 + Number(window.start.slice(3, 5)));
                return candidate + minimumDuration <= Math.min(endMinute, Number(window.end.slice(0, 2)) * 60 + Number(window.end.slice(3, 5)));
              });
            })) error = "Mỗi khoảng hôm nay cần còn đủ thời gian để khách đặt trước tối thiểu 120 phút.";
      }
      if (message) { message.textContent = error; message.hidden = !error; }
      submit.disabled = Boolean(error);
      return !error;
    };
    dateInput.addEventListener("change", validate);
    form.addEventListener("change", event => { if (event.target.matches("[data-time-trigger]")) validate(); });
    form.addEventListener("submit", event => { if (!validate()) event.preventDefault(); });
    if (form.hasAttribute("data-staff-multi-form")) {
      const rows = form.querySelector("[data-staff-interval-list]");
      form.querySelector("[data-add-staff-interval]")?.addEventListener("click", () => {
        if (rows.querySelectorAll("[data-staff-interval-row]").length >= 8) {
          if (message) { message.textContent = "Mỗi ngày đăng ký tối đa 8 khoảng giờ."; message.hidden = false; }
          return;
        }
        const row = rows.querySelector("[data-staff-interval-row]").cloneNode(true);
        row.querySelectorAll("[data-quarter-time]").forEach(picker => {
          picker.removeAttribute("data-time-picker-ready");
          picker.querySelector("[data-time-value]").value = "";
          const trigger = picker.querySelector("[data-time-trigger]");
          trigger.value = "";
          trigger.placeholder = "Chọn giờ";
        });
        rows.append(row);
        row.querySelectorAll("[data-quarter-time]").forEach(window.initQuarterTimePicker);
        validate();
      });
      form.addEventListener("click", event => {
        const remove = event.target.closest("[data-remove-staff-interval]");
        if (!remove) return;
        if (rows.querySelectorAll("[data-staff-interval-row]").length === 1) {
          if (message) { message.textContent = "Cần giữ lại ít nhất một khoảng giờ."; message.hidden = false; }
          return;
        }
        remove.closest("[data-staff-interval-row]").remove();
        validate();
      });
    }
    validate();
  });
}

document.querySelectorAll("[data-upload-form]").forEach(form => {
  const input = form.querySelector("input[type=file]");
  const status = form.querySelector("[data-file-status]");
  input.addEventListener("change", () => {
    const file = input.files[0];
    let error = "";
    if (file && (!/\.(jpe?g|png)$/i.test(file.name) || (file.type && !["image/jpeg", "image/png"].includes(file.type)))) {
      error = "Chỉ chọn ảnh JPEG hoặc PNG.";
    } else if (file && (file.size === 0 || file.size > 5 * 1024 * 1024)) {
      error = "Ảnh phải có dữ liệu và không vượt quá 5 MB.";
    }
    input.setCustomValidity(error);
    status.textContent = error || (file ? `${file.name} · ${(file.size / 1024 / 1024).toFixed(2)} MB` : "Chọn một ảnh mỗi lần tải.");
    status.classList.toggle("text-danger", Boolean(error));
  });
});

const bookingForm = document.querySelector("[data-booking-form]");
if (bookingForm) {
  const house = bookingForm.elements.houseId;
  const houseServiceAreaError = bookingForm.querySelector("#houseServiceAreaError");
  let houseAreaState = house.value ? "checking" : "empty";
  let houseAreaRequest = 0;
  const services = [...bookingForm.querySelectorAll("input[name=serviceIds]")];
  const calendarDates = bookingForm.querySelector("[data-available-dates]");
  const previousDatesButton = bookingForm.querySelector("[data-date-prev]");
  const nextDatesButton = bookingForm.querySelector("[data-date-next]");
  const calendarStatus = bookingForm.querySelector("[data-calendar-status]");
  const preferredStaffWrap = bookingForm.querySelector("[data-preferred-staff-wrap]");
  const preferredStaff = bookingForm.querySelector("[data-preferred-staff-select]");
  const selectedDates = bookingForm.querySelector("[data-selected-booking-dates]");
  const daysContainer = bookingForm.querySelector("[data-booking-days]");
  const staffSource = bookingForm.querySelector("[data-booking-staff-source]");
  const submit = bookingForm.querySelector("[data-booking-submit]");
  const summary = bookingForm.querySelector("[data-summary-services]");
  const message = bookingForm.querySelector("[data-booking-validation]");
  const allStaff = [...staffSource.options].slice(1).map(option => ({ id: option.value, name: option.textContent, staffType: option.dataset.staffType }));
  const currency = new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" });
  const params = new URLSearchParams(window.location.search);
  const requestedStaffId = params.get("staffId");
  if (!services.some(input => input.checked)) services.forEach(input => { input.checked = input.value === params.get("serviceId"); });
  const preferenceInputs = [...bookingForm.querySelectorAll("input[name=bookingPreference]")];
  let preference = requestedStaffId ? "staff" : "time";
  const preferenceInput = preferenceInputs.find(input => input.value === preference);
  if (preferenceInput) preferenceInput.checked = true;
  const days = new Map();
  let sequence = 0;
  let calendarSequence = 0;
  let pending = 0;
  let calendarPending = false;
  let calendarError = "";
  let calendarNotice = "";
  let availableDates = [];

  const DRAFT_KEY = "gonnha_booking_draft_v1";
  let draftSaveTimer = null;
  const scheduleDraftSave = () => {
    clearTimeout(draftSaveTimer);
    draftSaveTimer = setTimeout(() => {
      try {
        const chosenServices = services.filter(i => i.checked).map(i => i.value);
        const daysArr = [...days.entries()].map(([date, d]) => ({
          date,
          time: d.time || "",
          staffId: d.staffId ? String(d.staffId) : ""
        }));
        const noteEl = bookingForm.querySelector("#areaNote");
        const draft = {
          serviceIds: chosenServices,
          houseId: house.value || "",
          bookingPreference: preference,
          preferredStaffId: preferredStaff.value || "",
          areaNote: noteEl ? noteEl.value : "",
          days: daysArr,
          savedAt: Date.now()
        };
        if (chosenServices.length > 0 || house.value || daysArr.length > 0) {
          sessionStorage.setItem(DRAFT_KEY, JSON.stringify(draft));
        }
      } catch (e) {}
    }, 250);
  };
  const clearBookingDraft = () => {
    try { sessionStorage.removeItem(DRAFT_KEY); } catch (e) {}
  };
  const selectedServices = () => services.filter(input => input.checked);
  const timeFor = (day, time) => day.timeOptions.find(option => option.startTime === time);
  const weekdayDate = value => new Date(`${value}T12:00:00`).toLocaleDateString("vi-VN", { weekday: "long", day: "2-digit", month: "2-digit", year: "numeric" });
  const updateDateNavigation = () => {
    const maxScroll = Math.max(0, calendarDates.scrollWidth - calendarDates.clientWidth);
    previousDatesButton.disabled = maxScroll < 2 || calendarDates.scrollLeft < 2;
    nextDatesButton.disabled = maxScroll < 2 || calendarDates.scrollLeft > maxScroll - 2;
  };
  previousDatesButton.addEventListener("click", () => calendarDates.scrollBy({ left: -calendarDates.clientWidth * 0.85, behavior: "smooth" }));
  nextDatesButton.addEventListener("click", () => calendarDates.scrollBy({ left: calendarDates.clientWidth * 0.85, behavior: "smooth" }));
  calendarDates.addEventListener("scroll", updateDateNavigation, { passive: true });
  window.addEventListener("resize", updateDateNavigation);
  const hidden = (name, value) => { const input = document.createElement("input"); input.type = "hidden"; input.name = name; input.value = value; return input; };
  const addHiddenSelection = (card, day) => {
    card.querySelectorAll("input[data-booking-submission]").forEach(input => input.remove());
    if (day.time && day.staffId) {
      const date = hidden("workDates", day.date);
      const staffId = hidden("staffIds", day.staffId);
      const startTime = hidden("startTimes", day.time);
      [date, staffId, startTime].forEach(input => { input.dataset.bookingSubmission = ""; card.append(input); });
    }
  };
  const renderDateChips = () => {
    selectedDates.replaceChildren();
    [...days.keys()].sort().forEach(date => {
      const chip = document.createElement("span");
      chip.className = "booking-date-chip";
      const label = document.createElement("span"); label.textContent = weekdayDate(date);
      const remove = document.createElement("button"); remove.type = "button"; remove.className = "btn-close"; remove.setAttribute("aria-label", `Bỏ ngày ${weekdayDate(date)}`);
      remove.addEventListener("click", () => { days.delete(date); render(); scheduleDraftSave(); });
      chip.append(label, remove); selectedDates.append(chip);
    });
  };
  const staffCards = [...bookingForm.querySelectorAll('[data-staff-card-id]')];
  const syncStaffCards = () => {
    const currentId = preferredStaff.value;
    staffCards.forEach(card => {
      const isSelected = String(card.dataset.staffCardId) === String(currentId);
      card.classList.toggle('selected', isSelected);
      card.setAttribute('aria-selected', String(isSelected));
    });
  };
  staffCards.forEach(card => {
    card.addEventListener('click', () => {
      preferredStaff.value = card.dataset.staffCardId;
      preferredStaff.dispatchEvent(new Event('change'));
    });
    card.addEventListener('keydown', e => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        card.click();
      }
    });
  });

  const renderAvailableDates = () => {
    const previousScroll = calendarDates.scrollLeft;
    calendarDates.replaceChildren();
    preferredStaffWrap.hidden = preference !== 'staff';
    const navControls = bookingForm.querySelector('[data-date-nav-controls]');

    if (!selectedServices().length) {
      calendarStatus.textContent = 'Chọn dịch vụ ở Bước 01 để xem ngày có nhân viên làm.';
      if (navControls) navControls.style.display = 'none';
      if (previousDatesButton) previousDatesButton.style.display = 'none';
      if (nextDatesButton) nextDatesButton.style.display = 'none';
      const guide = document.createElement('div');
      guide.className = 'booking-dates-empty-guide';
      guide.innerHTML = '<div class="empty-guide-icon"><svg class="ui-icon" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect><line x1="16" y1="2" x2="16" y2="6"></line><line x1="8" y1="2" x2="8" y2="6"></line><line x1="3" y1="10" x2="21" y2="10"></line></svg></div><div class="empty-guide-text"><strong class="d-block mb-1 text-dark">Chưa chọn dịch vụ ở Bước 01</strong><p class="small text-secondary mb-0">Vui lòng tích chọn ít nhất một dịch vụ bạn cần ở trên để hệ thống tự động kiểm tra ngày làm việc của nhân viên.</p></div>';
      calendarDates.append(guide);
      updateDateNavigation();
      return;
    }

    if (preference === 'staff' && !preferredStaff.value) {
      calendarStatus.textContent = 'Chọn nhân viên trước; chỉ các ngày người đó có giờ phù hợp mới hiện.';
      if (navControls) navControls.style.display = 'none';
      if (previousDatesButton) previousDatesButton.style.display = 'none';
      if (nextDatesButton) nextDatesButton.style.display = 'none';
      const guide = document.createElement('div');
      guide.className = 'booking-dates-empty-guide';
      guide.innerHTML = '<div class="empty-guide-icon text-brand"><svg class="ui-icon" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path><circle cx="9" cy="7" r="4"></circle><path d="M23 21v-2a4 4 0 0 0-3-3.87"></path><path d="M16 3.13a4 4 0 0 1 0 7.75"></path></svg></div><div class="empty-guide-text"><strong class="d-block mb-1 text-dark">Vui lòng chọn nhân viên ưu tiên ở trên</strong><p class="small text-secondary mb-0">Nhấp chọn một nhân viên từ danh sách thẻ ở trên để xem ngay những ngày nhân viên đó có ca làm việc.</p></div>';
      calendarDates.append(guide);
      updateDateNavigation();
      return;
    }

    if (calendarPending) {
      calendarStatus.textContent = 'Đang kiểm tra ngày và lịch nhân viên…';
      if (navControls) navControls.style.display = 'none';
      const loading = document.createElement('div');
      loading.className = 'booking-dates-loading p-4 text-center w-100';
      loading.innerHTML = '<span class="spinner-border spinner-border-sm text-primary me-2" role="status"></span><span class="small text-secondary">Đang tải lịch ngày và kiểm tra nhân viên khả dụng…</span>';
      calendarDates.append(loading);
      updateDateNavigation();
      return;
    }

    if (calendarError) {
      calendarStatus.textContent = calendarError;
      if (navControls) navControls.style.display = 'none';
      const retry = document.createElement('button');
      retry.type = 'button';
      retry.className = 'btn btn-sm btn-outline-primary ms-2';
      retry.textContent = 'Tải lại ngày';
      retry.addEventListener('click', loadAvailableDates, { once: true });
      calendarStatus.append(retry);
      updateDateNavigation();
      return;
    }

    if (navControls) navControls.style.display = '';
    if (previousDatesButton) previousDatesButton.style.display = '';
    if (nextDatesButton) nextDatesButton.style.display = '';

    calendarStatus.textContent = calendarNotice || (availableDates.length
      ? 'Chỉ các ngày còn mốc giờ hợp lệ mới được hiển thị. Chọn một hoặc nhiều ngày.'
      : 'Không có ngày phù hợp trong 30 ngày tới.');

    if (!availableDates.length) {
      if (navControls) navControls.style.display = 'none';
      if (previousDatesButton) previousDatesButton.style.display = 'none';
      if (nextDatesButton) nextDatesButton.style.display = 'none';
      const emptyNotice = document.createElement('div');
      emptyNotice.className = 'booking-dates-empty-guide';
      emptyNotice.innerHTML = '<div class="empty-guide-icon text-muted"><svg class="ui-icon" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="12"></line><line x1="12" y1="16" x2="12.01" y2="16"></line></svg></div><div class="empty-guide-text"><strong class="d-block mb-1 text-dark">Không có ngày phù hợp trong 30 ngày tới</strong><p class="small text-secondary mb-0">Rất tiếc hiện chưa có ca làm việc nào trống đáp ứng thời lượng dịch vụ này. Vui lòng thử chọn nhân viên khác hoặc liên hệ hotline.</p></div>';
      calendarDates.append(emptyNotice);
      updateDateNavigation();
      return;
    }

    availableDates.forEach(date => {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'booking-available-date';
      const selected = days.has(date);
      button.classList.toggle('selected', selected);
      button.setAttribute('aria-pressed', String(selected));
      button.setAttribute('title', `Chọn ngày ${weekdayDate(date)}`);

      const d = new Date(`${date}T12:00:00`);
      const fullWeekday = d.toLocaleDateString('vi-VN', { weekday: 'long' });
      const dayNum = String(d.getDate()).padStart(2, '0');
      const monthNum = String(d.getMonth() + 1).padStart(2, '0');

      const weekday = document.createElement('span');
      weekday.className = 'date-card-weekday';
      weekday.textContent = fullWeekday;

      const dayLabel = document.createElement('span');
      dayLabel.className = 'date-card-day';
      dayLabel.textContent = dayNum;

      const monthYear = document.createElement('span');
      monthYear.className = 'date-card-month';
      monthYear.textContent = `Thg ${monthNum}`;

      button.append(weekday, dayLabel, monthYear);

      if (selected) {
        const checkBadge = document.createElement('span');
        checkBadge.className = 'date-card-check';
        checkBadge.innerHTML = '<svg class="ui-icon" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"></polyline></svg>';
        button.append(checkBadge);
      }

      button.addEventListener('click', () => toggleDate(date));
      calendarDates.append(button);
    });

    calendarDates.scrollLeft = Math.min(previousScroll, Math.max(0, calendarDates.scrollWidth - calendarDates.clientWidth));
    updateDateNavigation();
    requestAnimationFrame(updateDateNavigation);
  };
  async function loadAvailableDates() {
    const chosen=selectedServices();
    const request=++calendarSequence;
    calendarError=""; calendarNotice=""; availableDates=[];
    if(!chosen.length||(preference==="staff"&&!preferredStaff.value)) { calendarPending=false; renderAvailableDates(); refresh(); return; }
    calendarPending=true; renderAvailableDates(); refresh();
    const query=new URLSearchParams(); chosen.forEach(input=>query.append("serviceIds",input.value));
    if(preference==="staff"&&preferredStaff.value)query.set("staffId",preferredStaff.value);
    try {
      const response=await fetch(`/customer/bookings/availability-dates?${query}`,{headers:{Accept:"application/json"}});
      if(!response.ok)throw new Error("Không tải được danh sách ngày còn nhân viên làm.");
      const result=await response.json();
      if(request!==calendarSequence)return;
      availableDates=result.dates||[];
      const available=new Set(availableDates);
      const removed=[...days.keys()].filter(date=>!available.has(date));
      removed.forEach(date=>{const day=days.get(date);if(day){day.notice="Lựa chọn này không còn khả dụng; ngày và thông tin đã chọn được giữ lại để bạn điều chỉnh.";day.stale=true;}});
      if(removed.length)calendarNotice=`${removed.length} ngày đã chọn không còn phù hợp; thông tin được giữ lại để bạn điều chỉnh.`;
      else calendarNotice=result.message||"";
    } catch(error) {
      if(request===calendarSequence)calendarError="Không tải được lịch ngày. Vui lòng thử tải lại trước khi tiếp tục.";
    } finally {
      if(request===calendarSequence){calendarPending=false;render();}
    }
  }
  function toggleDate(date) {
    if(!availableDates.includes(date)||calendarPending)return;
    if(days.has(date))days.delete(date);
    else {
      days.forEach(day => { day.expanded = false; });
      days.set(date,{date,time:"",staffId:preference==="staff"?preferredStaff.value:"",loading:false,options:null,timeOptions:[],error:"",notice:"",expanded:true});
    }
    render();
    scheduleDraftSave();
    if(days.has(date))loadDay(days.get(date));
  }
  async function loadDay(day) {
    const chosen = selectedServices();
    if (!chosen.length) { day.options = null; day.loading = false; day.error = "Chọn ít nhất một dịch vụ để tra lịch."; render(); return; }
    const request = ++sequence; day.request = request; day.loading = true; day.error = ""; pending++; render();
    const query = new URLSearchParams({ workDate: day.date });
    if (house.value) query.set("houseId", house.value);
    chosen.forEach(input => query.append("serviceIds", input.value));
    if (day.staffId) query.set("staffId", day.staffId);
    if (day.time) query.set("startTime", day.time);
    try {
      const response = await fetch(`/customer/bookings/availability-options?${query}`, { headers: { Accept: "application/json" } });
      const options = await response.json().catch(()=>({}));
      if (!response.ok) throw new Error(options.detail || options.message || "Không tải được lịch khả dụng cho ngày này.");
      if (day.request !== request) return;
      day.options = options; day.error = "";
      day.timeOptions = options.timeOptions || [];
      if(options.earliestStartTime)day.timeOptions=day.timeOptions.filter(option=>option.startTime>=options.earliestStartTime);
      if (day.time && !day.timeOptions.some(option => option.startTime === day.time)) {
        if(!day.restored)day.time = "";
        day.stale=true;
        day.expanded=true;
        day.notice = options.message || "Mốc giờ này không còn phù hợp. Hãy chọn một giờ khác.";
      }
      if (day.staffId && day.time) {
        const eligible = timeFor(day, day.time)?.staffOptions || [];
        if (!eligible.some(person => String(person.id) === String(day.staffId))) {
          if(!day.restored)day.staffId = "";
          day.stale=true;
          day.notice = `Nhân viên không còn phù hợp với khung giờ này. ${options.message || "Hãy chọn người khác."}`;
        }
      }
    } catch (error) {
      if (day.request === request) { day.options = null; day.error = error.message; }
    } finally {
      pending = Math.max(0, pending - 1);
      if (day.request === request) { day.loading = false; render(); }
    }
  }
  const makeStaffSelect = (day, available, allowWithoutTime = false) => {
    const select = document.createElement("select"); select.className = "form-select booking-day-staff";
    select.append(new Option(preference === "staff" ? "Chọn nhân viên" : "Chọn giờ trước để xem nhân viên", ""));
    available.forEach(person => { const typeLabel = person.staffType === 'FULL_TIME' ? 'Toàn thời gian' : (person.staffType === 'PART_TIME' ? 'Bán thời gian' : ''); const label = person.name + (!typeLabel ? '' : ' (' + typeLabel + ')'); select.add(new Option(label, person.id)); });
    select.value = day.staffId || "";
    select.disabled = day.loading || !selectedServices().length || (preference === "time" && !day.time && !allowWithoutTime);
    select.addEventListener("change", () => {
      day.staffId = select.value;
      day.stale = false; day.restored = false;
      if (preference === "staff" || !timeFor(day, day.time)?.staffOptions.some(person => String(person.id) === String(day.staffId))) day.time = "";
      if (preference === "time" && day.time && day.staffId) day.expanded = false;
      day.notice = ""; loadDay(day);
    });
    return select;
  };
  const getDurationMinutes = () => {
    return selectedServices().reduce((sum, input) => sum + Number(input.dataset.duration || 0), 0);
  };
  const getEndTime = (start, dur) => {
    if (!start || !/^\d{2}:\d{2}$/.test(start) || !dur) return "";
    const [h, m] = start.split(":").map(Number);
    const total = h * 60 + m + dur;
    const endH = Math.floor(total / 60) % 24;
    const endM = total % 60;
    return (endH < 10 ? "0" : "") + endH + ":" + (endM < 10 ? "0" : "") + endM;
  };
  const renderDay = day => {
    const durationMinutes = getDurationMinutes();
    const card = document.createElement("article");
    card.className = "booking-day-card" + (day.time && day.staffId ? " is-confirmed" : "");
    card.dataset.bookingDate = day.date;

    const head = document.createElement("header");
    head.className = "booking-day-card-heading";

    const titleWrap = document.createElement("div");
    titleWrap.className = "d-flex align-items-center flex-wrap gap-2";
    const title = document.createElement("strong");
    title.className = "booking-day-card-title";
    title.textContent = weekdayDate(day.date);
    titleWrap.append(title);

    if (day.time && day.staffId) {
      const badge = document.createElement("span");
      badge.className = "badge";
      badge.style.background = "var(--brand-subtle)";
      badge.style.color = "var(--brand-dark)";
      badge.style.border = "1px solid var(--brand-border)";
      badge.style.fontWeight = "600";
      badge.textContent = "✓ " + day.time + " – " + getEndTime(day.time, durationMinutes);
      titleWrap.append(badge);
    } else {
      const badge = document.createElement("span");
      badge.className = "badge bg-light text-secondary border";
      badge.textContent = "Chưa hoàn tất chọn giờ";
      titleWrap.append(badge);
    }

    const actions = document.createElement("div");
    actions.className = "booking-day-card-actions";
    const edit = document.createElement("button");
    edit.type = "button";
    edit.className = "btn btn-sm btn-outline-primary";
    const selectedStaffName = allStaff.find(person => String(person.id) === String(day.staffId))?.name;
    edit.textContent = day.expanded
      ? "Thu gọn"
      : (day.time && day.staffId ? day.time + " · " + (selectedStaffName || "Đã chọn") + " · Sửa" : "Chọn giờ và nhân viên");
    edit.setAttribute("aria-expanded", String(Boolean(day.expanded)));
    edit.addEventListener("click", () => {
      days.forEach(item => { item.expanded = item === day ? !day.expanded : false; });
      render();
    });

    const remove = document.createElement("button");
    remove.type = "button";
    remove.className = "btn btn-sm btn-outline-danger";
    remove.textContent = "Bỏ ngày";
    remove.addEventListener("click", () => { days.delete(day.date); render(); scheduleDraftSave(); });

    actions.append(edit, remove);
    head.append(titleWrap, actions);
    card.append(head);

    // Confirmed banner
    if (day.time && day.staffId) {
      const staffObj = allStaff.find(p => String(p.id) === String(day.staffId));
      const sName = staffObj?.name || selectedStaffName || "Nhân viên";
      const sType = staffObj?.staffType === 'FULL_TIME' ? 'Toàn thời gian' : (staffObj?.staffType === 'PART_TIME' ? 'Bán thời gian' : '');
      const sAvatar = sName.trim().charAt(0) || 'N';
      const confirmedBox = document.createElement("div");
      confirmedBox.className = "booking-day-confirmed-card";
      confirmedBox.innerHTML = `
        <div class="booking-day-confirmed-left">
          <div class="booking-day-confirmed-time">
            <svg class="ui-icon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline></svg>
            <div>
              <strong>${day.time} – ${getEndTime(day.time, durationMinutes)}</strong>
              <small class="d-block text-secondary">Thời lượng dịch vụ: ${durationMinutes} phút</small>
            </div>
          </div>
          <div class="booking-day-confirmed-staff">
            <div class="booking-day-confirmed-avatar">${sAvatar}</div>
            <div>
              <strong>${sName}</strong>
              <small class="d-block text-secondary">${sType ? sType : 'Nhân viên thực hiện'}</small>
            </div>
          </div>
        </div>
        <div class="booking-day-confirmed-status">
          <span class="badge" style="background: #eaf7ec; color: #08752e; border: 1px solid #b8dfbe; font-size: 0.82rem; padding: 0.45rem 0.75rem;">
            ✓ Sẵn sàng thực hiện
          </span>
        </div>
      `;
      card.append(confirmedBox);
    } else if (!day.expanded) {
      const unconfBox = document.createElement("div");
      unconfBox.className = "booking-day-unconfigured-note d-flex align-items-center justify-content-between p-2 px-3 rounded-2 mt-1";
      unconfBox.style.background = "var(--bg-subtle)";
      unconfBox.style.fontSize = "0.85rem";
      unconfBox.innerHTML = `
        <span class="text-secondary">⚠️ Vui lòng hoàn tất chọn giờ và nhân viên để hoàn thiện lịch ngày này.</span>
        <button type="button" class="btn btn-sm btn-link text-decoration-none text-primary p-0 fw-semibold">Chọn ngay →</button>
      `;
      unconfBox.querySelector("button").addEventListener("click", () => {
        days.forEach(item => { item.expanded = item === day; });
        render();
      });
      card.append(unconfBox);
    }

    const status = document.createElement("p");
    status.className = "small text-secondary booking-day-status mt-2 mb-0";
    status.setAttribute("role", "status");
    status.textContent = day.loading
      ? "Đang tải lịch…"
      : day.error || day.notice || day.options?.message || (day.time && day.staffId ? "Đã chọn giờ và nhân viên cho ngày này." : "Chọn giờ và nhân viên cho ngày này.");
    status.classList.toggle("text-danger", Boolean(day.error || day.notice));
    status.classList.toggle("text-secondary", !day.error && !day.notice);
    card.append(status);

    addHiddenSelection(card, day);
    if (!day.expanded) return card;

    const eligibleStaff = day.time
      ? (timeFor(day, day.time)?.staffOptions || [])
      : [...new Map((day.timeOptions || []).flatMap(option => option.staffOptions || []).map(person => [String(person.id), person])).values()];

    const timeSection = document.createElement("div");
    timeSection.className = "booking-time-section mt-3";

    if (preference === "staff") {
      if (!day.staffId) {
        const prompt = document.createElement("div");
        prompt.className = "alert alert-warning py-2 px-3 small mb-0";
        prompt.textContent = "Vui lòng chọn một nhân viên ưu tiên ở Bước 03 phía trên để xem các ca làm việc của họ.";
        timeSection.append(prompt);
      } else if (!day.loading && day.options) {
        const timeHeader = document.createElement("div");
        timeHeader.className = "booking-section-subhead d-flex align-items-center justify-content-between mb-2";
        timeHeader.innerHTML = `
          <span class="fw-semibold text-dark d-flex align-items-center gap-2">
            <svg class="ui-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline></svg>
            <span>Chọn khung giờ bắt đầu (Ca làm việc: ${durationMinutes} phút)</span>
          </span>
          <span class="small text-secondary">${(day.timeOptions || []).length} khung giờ trống</span>
        `;
        timeSection.append(timeHeader);

        const morning = (day.timeOptions || []).filter(opt => opt.startTime < "12:00");
        const afternoon = (day.timeOptions || []).filter(opt => opt.startTime >= "12:00");

        const createGroup = (label, iconChar, opts) => {
          if (!opts.length) return null;
          const wrap = document.createElement("div");
          wrap.className = "booking-time-group mb-2";
          wrap.innerHTML = `<div class="booking-time-session-label"><span>${iconChar}</span> <span>${label} (${opts.length} mốc)</span></div>`;
          const grid = document.createElement("div");
          grid.className = "booking-time-grid";
          opts.forEach(option => {
            const btn = document.createElement("button");
            btn.type = "button";
            btn.className = "booking-time-slot-btn" + (day.time === option.startTime ? " selected" : "");
            btn.setAttribute("aria-pressed", String(day.time === option.startTime));
            btn.innerHTML = `<strong>${option.startTime}</strong><small>${getEndTime(option.startTime, durationMinutes)}</small>`;
            btn.addEventListener("click", () => {
              day.time = option.startTime;
              day.stale = false; day.restored = false; day.notice = "";
              day.expanded = false;
              render(); scheduleDraftSave(); loadDay(day);
            });
            grid.append(btn);
          });
          wrap.append(grid);
          return wrap;
        };

        const mg = createGroup("Buổi sáng (08:00 – 12:00)", "🌅", morning);
        const ag = createGroup("Buổi chiều & Tối (12:00 – 18:00)", "☀️", afternoon);
        if (mg) timeSection.append(mg);
        if (ag) timeSection.append(ag);
        if (!mg && !ag) {
          const empty = document.createElement("p");
          empty.className = "small text-secondary mb-0";
          empty.textContent = day.options.message || "Nhân viên này chưa có giờ phù hợp trong ngày.";
          timeSection.append(empty);
        }
      }
    } else {
      // PREFERENCE === "TIME" (Default mode)
      const timeHeader = document.createElement("div");
      timeHeader.className = "booking-section-subhead d-flex align-items-center justify-content-between mb-2";
      timeHeader.innerHTML = `
        <span class="fw-semibold text-dark d-flex align-items-center gap-2">
          <svg class="ui-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline></svg>
          <span>Bước 1: Chọn giờ bắt đầu làm việc</span>
        </span>
        <span class="small text-secondary">Mỗi ca kéo dài ${durationMinutes} phút</span>
      `;
      timeSection.append(timeHeader);

      if (day.timeOptions && day.timeOptions.length > 0) {
        const morning = day.timeOptions.filter(opt => opt.startTime < "12:00");
        const afternoon = day.timeOptions.filter(opt => opt.startTime >= "12:00");

        const createGroup = (label, iconChar, opts) => {
          if (!opts.length) return null;
          const wrap = document.createElement("div");
          wrap.className = "booking-time-group mb-2";
          wrap.innerHTML = `<div class="booking-time-session-label"><span>${iconChar}</span> <span>${label} (${opts.length} mốc khả dụng)</span></div>`;
          const grid = document.createElement("div");
          grid.className = "booking-time-grid";
          opts.forEach(option => {
            const btn = document.createElement("button");
            btn.type = "button";
            btn.className = "booking-time-slot-btn" + (day.time === option.startTime ? " selected" : "");
            btn.setAttribute("aria-pressed", String(day.time === option.startTime));
            btn.innerHTML = `<strong>${option.startTime}</strong><small>${getEndTime(option.startTime, durationMinutes)}</small>`;
            btn.addEventListener("click", () => {
              day.time = option.startTime;
              day.stale = false; day.restored = false; day.notice = "";
              const elig = timeFor(day, option.startTime)?.staffOptions || [];
              if (!elig.some(p => String(p.id) === String(day.staffId))) {
                if (elig.length === 1) {
                  day.staffId = elig[0].id;
                } else {
                  day.staffId = "";
                }
              }
              render(); scheduleDraftSave(); loadDay(day);
            });
            grid.append(btn);
          });
          wrap.append(grid);
          return wrap;
        };

        const mg = createGroup("Buổi sáng (08:00 – 12:00)", "🌅", morning);
        const ag = createGroup("Buổi chiều & Tối (12:00 – 18:00)", "☀️", afternoon);
        if (mg) timeSection.append(mg);
        if (ag) timeSection.append(ag);
      }

      // Quarter-time picker fallback
      const pickerLabel = document.createElement("details");
      pickerLabel.className = "booking-time-custom-details mt-2";
      pickerLabel.innerHTML = `<summary class="small text-secondary fw-semibold cursor-pointer mb-2">⚙️ Hoặc tùy chỉnh mốc giờ khác</summary>`;
      const pickerField = document.createElement("div");
      pickerField.className = "booking-day-field booking-day-time-field";
      const picker = document.createElement("div");
      picker.className = "time-picker";
      picker.dataset.quarterTime = "";
      picker.dataset.bookingTimePicker = "";
      picker.dataset.changeOnDone = "";
      const trigger = document.createElement("input");
      trigger.type = "text";
      trigger.className = "form-control time-trigger";
      trigger.dataset.timeTrigger = "";
      trigger.readOnly = true;
      trigger.disabled = day.loading || !day.options || !selectedServices().length;
      trigger.placeholder = "Chọn giờ tùy ý";
      trigger.setAttribute("aria-label", "Giờ bắt đầu " + weekdayDate(day.date));
      trigger.value = day.time || "";
      const value = document.createElement("input");
      value.type = "hidden";
      value.dataset.timeValue = "";
      value.value = day.time || "";
      picker.append(trigger, value);
      pickerField.append(picker);
      pickerLabel.append(pickerField);
      timeSection.append(pickerLabel);

      if (window.initQuarterTimePicker) window.initQuarterTimePicker(picker);
      picker.setAllowedTimes?.((day.timeOptions || []).map(option => option.startTime));
      picker.addEventListener("change", () => {
        day.time = value.value;
        day.staffId = "";
        day.stale = false; day.restored = false; day.notice = "";
        render(); loadDay(day);
      });
    }

    card.append(timeSection);

    // SECTION 2: Chọn nhân viên thực hiện (khi preference === "time")
    if (preference === "time") {
      const staffSection = document.createElement("div");
      staffSection.className = "booking-staff-section mt-3 pt-3 border-top";

      if (day.time) {
        const staffHeader = document.createElement("div");
        staffHeader.className = "booking-section-subhead d-flex align-items-center justify-content-between mb-2";
        staffHeader.innerHTML = `
          <span class="fw-semibold text-dark d-flex align-items-center gap-2">
            <svg class="ui-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2"></path><circle cx="12" cy="7" r="4"></circle></svg>
            <span>Bước 2: Chọn nhân viên cho ca ${day.time} – ${getEndTime(day.time, durationMinutes)}</span>
          </span>
          <span class="small text-secondary">${eligibleStaff.length} nhân viên sẵn sàng</span>
        `;
        staffSection.append(staffHeader);

        const staffSelect = makeStaffSelect(day, eligibleStaff);
        staffSelect.hidden = true;
        staffSection.append(staffSelect);

        const staffGrid = document.createElement("div");
        staffGrid.className = "booking-day-staff-grid";
        eligibleStaff.forEach(person => {
          const isSelected = String(day.staffId) === String(person.id);
          const sCard = document.createElement("div");
          sCard.className = "booking-day-staff-card" + (isSelected ? " selected" : "");
          sCard.tabIndex = 0;
          sCard.role = "button";
          const typeLabel = person.staffType === 'FULL_TIME' ? 'Toàn thời gian' : (person.staffType === 'PART_TIME' ? 'Bán thời gian' : '');
          const avatarChar = (person.name || 'N').trim().charAt(0);
          sCard.innerHTML = `
            <div class="booking-day-staff-avatar">${avatarChar}</div>
            <div class="booking-day-staff-details">
              <span class="booking-day-staff-name">${person.name}</span>
              <span class="booking-day-staff-type">${typeLabel}</span>
            </div>
            <span class="booking-day-staff-check" ${isSelected ? '' : 'style="display:none;"'}>
              <svg class="ui-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"></polyline></svg>
            </span>
          `;
          sCard.addEventListener("click", () => {
            day.staffId = person.id;
            staffSelect.value = person.id;
            day.expanded = false;
            day.notice = "";
            render();
            scheduleDraftSave();
            loadDay(day);
          });
          staffGrid.append(sCard);
        });
        staffSection.append(staffGrid);
      } else {
        const prompt = document.createElement("p");
        prompt.className = "small text-secondary mb-0 p-2 rounded-2";
        prompt.style.background = "var(--bg-subtle)";
        prompt.textContent = "👆 Hãy chọn một khung giờ ở Bước 1 trước để xem danh sách các nhân viên có thể nhận ca.";
        staffSection.append(prompt);
      }
      card.append(staffSection);
    } else if (day.stale && !day.loading && !day.timeOptions.length) {
      const staffLabel = document.createElement("label");
      staffLabel.className = "booking-day-field mt-3";
      const staffCaption = document.createElement("span");
      staffCaption.textContent = "Đổi nhân viên để xem các giờ khác";
      staffLabel.append(staffCaption, makeStaffSelect(day, allStaff, true));
      card.append(staffLabel);
    }

    return card;
  };
  function render() {
    renderDateChips(); renderAvailableDates(); daysContainer.replaceChildren();
    const ordered = [...days.values()].sort((a,b) => a.date.localeCompare(b.date));
    if (!ordered.length) { const empty = document.createElement("div"); empty.className = "booking-time-empty-guide text-center py-4 px-3 surface border rounded-3 w-100"; empty.innerHTML = '<div class="d-inline-flex align-items-center justify-content-center p-3 rounded-circle mb-2" style="background: var(--bg-subtle); color: var(--muted);"><svg class="ui-icon" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline></svg></div><strong class="d-block mb-1 text-dark">Chưa có ngày nào được chọn</strong><p class="small text-secondary mb-0">Thêm ít nhất một ngày để xem lịch.</p>'; daysContainer.append(empty); }
    ordered.forEach(day => daysContainer.append(renderDay(day)));
    refresh();
  }
  preferredStaff.value=requestedStaffId||"";
  preferredStaff.addEventListener("change", () => { syncStaffCards();
    days.forEach(day=>{day.time="";day.staffId=preferredStaff.value;day.stale=false;day.restored=false;day.notice="";});
    render(); loadAvailableDates(); days.forEach(loadDay);
    scheduleDraftSave();
  });
  preferenceInputs.forEach(input => input.addEventListener("change", () => {
    preference=input.value; days.forEach(day=>{day.time="";day.staffId=preference==="staff"?preferredStaff.value:"";day.stale=false;day.restored=false;day.notice="";});
    if(preference==="staff"&&!preferredStaff.value)days.clear(); syncStaffCards();
    render(); loadAvailableDates(); days.forEach(loadDay);
    scheduleDraftSave();
  }));
  services.forEach(input => input.addEventListener("change", () => {
    days.forEach(day=>{day.time="";day.staffId=preference==="staff"?preferredStaff.value:"";day.stale=false;day.restored=false;day.notice="";});
    render(); loadAvailableDates(); days.forEach(loadDay);
    scheduleDraftSave();
  }));
  async function checkHouseServiceArea() {
    const request=++houseAreaRequest;
    if(!house.value){houseAreaState="empty";if(houseServiceAreaError){houseServiceAreaError.hidden=true;houseServiceAreaError.textContent="";}refresh();return;}
    houseAreaState="checking";
    if(houseServiceAreaError){houseServiceAreaError.hidden=false;houseServiceAreaError.classList.remove("text-danger");houseServiceAreaError.classList.add("text-secondary");houseServiceAreaError.textContent="Đang kiểm tra địa chỉ trong bán kính phục vụ…";}
    refresh();
    try {
      const response=await fetch(`/customer/bookings/house-service-area?houseId=${encodeURIComponent(house.value)}`,{headers:{Accept:"application/json"}});
      if(!response.ok)throw new Error();
      const result=await response.json();
      if(request!==houseAreaRequest)return;
      houseAreaState=result.eligible?"eligible":"ineligible";
      if(houseServiceAreaError){houseServiceAreaError.hidden=false;houseServiceAreaError.classList.toggle("text-danger",!result.eligible);houseServiceAreaError.classList.toggle("text-secondary",Boolean(result.eligible));houseServiceAreaError.textContent=result.message;}
    } catch(error) {
      if(request!==houseAreaRequest)return;
      houseAreaState="error";
      if(houseServiceAreaError){houseServiceAreaError.hidden=false;houseServiceAreaError.classList.add("text-danger");houseServiceAreaError.textContent="Chưa kiểm tra được bán kính phục vụ. Vui lòng thử lại.";}
    }
    refresh();
  }
  house.addEventListener("change",()=>{
    checkHouseServiceArea();
    days.forEach(day=>{day.time="";day.staffId=preference==="staff"?preferredStaff.value:"";day.stale=false;day.restored=false;day.notice="";});
    render();days.forEach(loadDay);
    scheduleDraftSave();
  });
  function refresh() {
    const selected = services.filter(input => input.checked);
    const minutes = selected.reduce((sum, input) => sum + Number(input.dataset.duration), 0);
    const price = selected.reduce((sum, input) => sum + Number(input.dataset.price), 0);
    summary.replaceChildren();
    selected.forEach(input => {
      const item = document.createElement("li");
      item.textContent = `${input.dataset.name} · ${input.dataset.duration} phút`;
      summary.append(item);
    });
    if (!selected.length) {
      const empty = document.createElement("li"); empty.textContent = "Chưa chọn dịch vụ"; summary.append(empty);
    }
    const selectedDays = [...days.values()];
    bookingForm.querySelector("[data-summary-duration]").textContent = `${minutes} phút × ${selectedDays.length} ngày`;
    bookingForm.querySelector("[data-summary-price]").textContent = `${currency.format(price * selectedDays.length)} tổng · ${currency.format(price)} / ngày`;
    const incomplete = selectedDays.filter(day => !day.options || day.stale || !day.time || !day.staffId || !timeFor(day, day.time)?.staffOptions.some(person => String(person.id) === String(day.staffId)));
    let error = "";
    if (!selected.length) error = "Chọn ít nhất một dịch vụ.";
    else if (!house.value) error = "Chọn địa chỉ nhà cần dọn.";
    else if (houseAreaState === "checking") error = "Đang kiểm tra địa chỉ trong bán kính phục vụ.";
    else if (houseAreaState === "ineligible" || houseAreaState === "error" || houseAreaState === "empty") error = "Hãy chọn địa chỉ nhà nằm trong bán kính phục vụ.";
    else if (preference==="staff"&&!preferredStaff.value) error = "Chọn nhân viên ưu tiên trước khi xem ngày làm.";
    else if (!selectedDays.length) error = "Chọn ít nhất một ngày có nhân viên sẵn sàng.";
    else if (calendarPending||pending) error = "Đang tải lịch khả dụng cho các ngày đã chọn.";
    else if (calendarError) error = "Không thể đặt khi chưa tải được danh sách ngày khả dụng.";
    else if (incomplete.length) error = `Hãy chọn giờ và nhân viên cho từng ngày, hoặc bỏ ngày chưa phù hợp (${incomplete.length} ngày).`;
    message.textContent = error;
    submit.disabled = Boolean(error);
  }
  bookingForm.addEventListener("submit", event => {
    refresh();
    if (submit.disabled || !bookingForm.reportValidity()) {
      event.preventDefault();
    } else {
      clearBookingDraft();
    }
  });
  const retryState=window.bookingRetryState;
  let restoredFromDraft = false;
  if(retryState){
    if(retryState.houseId)house.value=String(retryState.houseId);
    if(Array.isArray(retryState.serviceIds))services.forEach(input=>input.checked=retryState.serviceIds.map(String).includes(input.value));
    const note=bookingForm.querySelector("#areaNote");if(note&&retryState.areaNote)note.value=retryState.areaNote;
    if(Array.isArray(retryState.days))retryState.days.forEach(item=>{
      if(item&&item.date&&item.staffId&&item.time)days.set(item.date,{date:item.date,time:item.time,staffId:String(item.staffId),loading:false,options:null,timeOptions:[],error:"",notice:"Lịch trước chưa được tạo. Hãy xác nhận lại lựa chọn.",expanded:true,restored:true,stale:false});
    });
  } else {
    try {
      const raw = sessionStorage.getItem(DRAFT_KEY);
      if (raw) {
        const draft = JSON.parse(raw);
        if (draft && typeof draft === "object") {
          if (Array.isArray(draft.serviceIds) && draft.serviceIds.length > 0) {
            services.forEach(input => {
              input.checked = draft.serviceIds.map(String).includes(String(input.value));
            });
            restoredFromDraft = true;
          }
          if (draft.houseId && [...house.options].some(opt => opt.value === String(draft.houseId))) {
            house.value = String(draft.houseId);
            restoredFromDraft = true;
          }
          if (draft.bookingPreference) {
            preference = draft.bookingPreference;
            const pInput = preferenceInputs.find(i => i.value === preference);
            if (pInput) pInput.checked = true;
            preferredStaffWrap.hidden = preference !== "staff";
            restoredFromDraft = true;
          }
          if (draft.preferredStaffId) {
            preferredStaff.value = String(draft.preferredStaffId);
          }
          const note = bookingForm.querySelector("#areaNote");
          if (note && draft.areaNote) note.value = draft.areaNote;
          if (Array.isArray(draft.days) && draft.days.length > 0) {
            draft.days.forEach(item => {
              if (item && item.date) {
                days.set(item.date, {
                  date: item.date,
                  time: item.time || "",
                  staffId: item.staffId ? String(item.staffId) : (preference === "staff" ? preferredStaff.value : ""),
                  loading: false,
                  options: null,
                  timeOptions: [],
                  error: "",
                  notice: "",
                  expanded: !item.time || !item.staffId,
                  restored: true,
                  stale: false
                });
                restoredFromDraft = true;
              }
            });
          }
        }
      }
    } catch (e) {
      console.warn("Could not restore booking draft", e);
    }
  }

  if (restoredFromDraft) {
    const noticeEl = document.querySelector("#bookingDraftNotice");
    if (noticeEl) {
      noticeEl.hidden = false;
      noticeEl.className = "alert alert-info d-flex align-items-center justify-content-between p-3 mb-3 border-0 shadow-sm rounded-3";
      noticeEl.style.cssText = "background-color: var(--brand-subtle); color: var(--brand-dark); border-left: 4px solid var(--brand) !important;";
      noticeEl.innerHTML = `
        <div class="d-flex align-items-center gap-2">
          <span style="font-size: 1.15rem;">✨</span>
          <span>Đã tự động lưu và khôi phục các dịch vụ, nhà và ngày giờ bạn đang điền dang dở.</span>
        </div>
        <button type="button" class="btn btn-sm btn-outline-secondary" id="clearDraftBtn">Xóa & Làm lại</button>
      `;
      noticeEl.querySelector("#clearDraftBtn")?.addEventListener("click", () => {
        clearBookingDraft();
        window.location.reload();
      });
    }
  }

  const areaNoteInput = bookingForm.querySelector("#areaNote");
  if (areaNoteInput) areaNoteInput.addEventListener("input", scheduleDraftSave);

  syncStaffCards(); render(); loadAvailableDates(); checkHouseServiceArea();
  if(retryState?.days?.length)retryState.days.forEach(item=>{const day=days.get(item.date);if(day)loadDay(day);});
  else if (restoredFromDraft && days.size > 0) days.forEach(day => loadDay(day));
}

if (document.querySelector(".admin-sidebar")) {
  document.body.classList.add("has-admin-sidebar");
}

// Sync "Nghỉ" operating-hours rows (fallback alongside CSS :has(input:checked))
document.querySelectorAll("[data-hours-row]").forEach(row => {
  const toggle = row.querySelector("[data-closed-toggle]");
  if (!toggle) return;
  const timeInputs = [...row.querySelectorAll(".hours-time-input")];
  const syncClosedState = () => {
    const closed = toggle.checked;
    row.classList.toggle("is-closed-day", closed);
    timeInputs.forEach(input => {
      if (closed) {
        input.setAttribute("aria-disabled", "true");
        input.setAttribute("tabindex", "-1");
      } else {
        input.removeAttribute("aria-disabled");
        input.removeAttribute("tabindex");
      }
    });
  };
  toggle.addEventListener("change", syncClosedState);
  syncClosedState();
});

// Expand collapsed <details> automatically if a child control triggers validation
document.addEventListener("invalid", event => {
  const hiddenDetails = event.target instanceof Element ? event.target.closest("details:not([open])") : null;
  if (hiddenDetails) hiddenDetails.open = true;
}, true);

// Tactile loading feedback on valid form submission
document.querySelectorAll("form").forEach(form => {
  form.addEventListener("submit", event => {
    if (event.defaultPrevented || !form.checkValidity()) return;
    const submitter = event.submitter || form.querySelector('button[type="submit"]');
    if (submitter) submitter.classList.add("is-submitting");
  });
});

// Replay house sketch line-drawing animation on click
const replayHouseSketchBtn = document.getElementById("replayHouseSketch");
if (replayHouseSketchBtn) {
  replayHouseSketchBtn.addEventListener("click", () => {
    const viewport = document.getElementById("sketchViewport");
    if (!viewport) return;
    const svg = viewport.querySelector(".sketch-svg");
    if (!svg) return;

    // Tạm gỡ class .is-alive khi vẽ lại
    viewport.classList.remove("is-alive");

    // Replay SVG stroke and fill animations cleanly
    const clone = svg.cloneNode(true);
    svg.parentNode.replaceChild(clone, svg);

    // Kích hoạt lại .is-alive sau 2.8s
    setTimeout(() => {
      viewport.classList.add("is-alive");
    }, 2800);
  });
}

// Senior UI/UX Performance & Living Animations Controller
document.addEventListener('DOMContentLoaded', () => {
  const house = document.getElementById('sketchViewport') || document.querySelector('.house');

  if (house) {
    // 1. IntersectionObserver: Tạm dừng hoạt ảnh khi Hero cuộn ra khỏi viewport để tối ưu GPU
    if ('IntersectionObserver' in window) {
      const observer = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
          if (entry.isIntersecting) {
            house.classList.add('is-visible');
          } else {
            house.classList.remove('is-visible');
          }
        });
      }, {
        root: null,
        threshold: 0.05
      });
      observer.observe(house);
    } else {
      house.classList.add('is-visible');
    }

    // 2. Kích hoạt hoạt ảnh sống động vô hạn sau khi chuỗi xuất hiện ban đầu hoàn tất (~2.8s)
    setTimeout(() => {
      house.classList.add('is-alive');
    }, 2800);
  }

  // 3. Gỡ bỏ will-change sau 3 giây để giải phóng GPU memory
  setTimeout(() => {
    document.querySelectorAll('.will-change-anim').forEach(el => {
      el.style.willChange = 'auto';
    });
  }, 3000);

  // 4. Khởi tạo Apple/iOS Header, Segmented Control, Bottom Sheet & Scroll Reveal
  initAppleHeaderAndScroll();
  initSegmentedControl();
  initMobileSheet();
  initScrollReveal();
});

// ==========================================================================
// APPLE/IOS UI CONTROLLER: Header, Segmented Control, Bottom Sheet & Reveal
// ==========================================================================

// Đánh dấu JS đã sẵn sàng cho HTML
document.documentElement.classList.add('js');

// 1. Header Sticky, .is-scrolled (>24px) và Apple Scroll Progress Bar
function initAppleHeaderAndScroll() {
  const header = document.getElementById('siteHeader') || document.querySelector('.site-header');
  const progressBar = document.getElementById('headerScrollProgress');
  const bannerImg = document.getElementById('parallaxBannerImg');

  let ticking = false;
  window.addEventListener('scroll', () => {
    if (!ticking) {
      window.requestAnimationFrame(() => {
        const scrollY = window.scrollY || window.pageYOffset;

        // Thu gọn chiều cao header & viền kính mờ
        if (header) {
          if (scrollY > 24) header.classList.add('is-scrolled');
          else header.classList.remove('is-scrolled');
        }

        // Cập nhật thanh tiến độ cuộn 3px
        if (progressBar) {
          const total = document.documentElement.scrollHeight - window.innerHeight;
          const ratio = total > 0 ? Math.min(1, Math.max(0, scrollY / total)) : 0;
          progressBar.style.transform = `scaleX(${ratio})`;
        }

        // Parallax ảnh banner
        if (bannerImg && window.innerWidth >= 768) {
          const banner = bannerImg.closest('.care-banner');
          if (banner) {
            const rect = banner.getBoundingClientRect();
            const vh = window.innerHeight;
            if (rect.bottom >= 0 && rect.top <= vh) {
              const centerDiff = (rect.top + rect.height / 2) - (vh / 2);
              const offset = Math.max(-20, Math.min(20, -centerDiff * 0.06));
              bannerImg.style.transform = `translateY(${offset.toFixed(1)}px) scale(1.05)`;
            }
          }
        }

        ticking = false;
      });
      ticking = true;
    }
  }, { passive: true });
}

// 2. iOS Segmented Control: Viên thuốc trượt theo tab hoạt động
function initSegmentedControl() {
  const controls = document.querySelectorAll('.ios-segmented-control');
  controls.forEach(control => {
    const indicator = control.querySelector('.ios-segmented-indicator');
    const items = control.querySelectorAll('.ios-segmented-item');
    if (!indicator || !items.length) return;

    function moveTo(item) {
      if (!item) return;
      indicator.style.width = `${item.offsetWidth}px`;
      indicator.style.transform = `translateX(${item.offsetLeft}px)`;
    }

    const activeItem = control.querySelector('.ios-segmented-item.active') || items[0];

    // Căn vị trí ban đầu
    indicator.style.transition = 'none';
    moveTo(activeItem);

    requestAnimationFrame(() => {
      indicator.style.transition = '';
    });

    items.forEach(item => {
      item.addEventListener('mouseenter', () => moveTo(item));
      item.addEventListener('mouseleave', () => moveTo(activeItem));
    });

    window.addEventListener('resize', () => {
      const cur = control.querySelector('.ios-segmented-item.active') || items[0];
      moveTo(cur);
    }, { passive: true });
  });
}

// 3. Mobile iOS Bottom Sheet (< 768px)
function initMobileSheet() {
  const toggleBtn = document.getElementById('iosMenuToggleBtn');
  const sheet = document.getElementById('iosMobileSheet');
  const backdrop = document.getElementById('iosSheetBackdrop');
  const closeBtn = document.getElementById('iosSheetCloseBtn');
  if (!toggleBtn || !sheet) return;

  function openSheet() {
    sheet.classList.add('is-open');
    sheet.setAttribute('aria-hidden', 'false');
    document.body.style.overflow = 'hidden';
  }

  function closeSheet() {
    sheet.classList.remove('is-open');
    sheet.setAttribute('aria-hidden', 'true');
    document.body.style.overflow = '';
  }

  toggleBtn.addEventListener('click', openSheet);
  if (backdrop) backdrop.addEventListener('click', closeSheet);
  if (closeBtn) closeBtn.addEventListener('click', closeSheet);

  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && sheet.classList.contains('is-open')) {
      closeSheet();
    }
  });
}

// 4. Scroll Reveal PowerPoint Slide Style & Price Counter Animation
function initScrollReveal() {
  const revealElements = document.querySelectorAll('[data-reveal]');
  if (!revealElements.length) return;

  if (!('IntersectionObserver' in window)) {
    revealElements.forEach(el => el.classList.add('is-in'));
    return;
  }

  const observer = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        const el = entry.target;
        const delay = parseInt(el.getAttribute('data-delay') || '0', 10);
        if (delay > 0) {
          el.style.transitionDelay = `${delay}ms`;
        }
        el.classList.add('is-in');
        observer.unobserve(el);

        // Kích hoạt animation đếm số cho giá tiền khi thẻ xuất hiện
        const prices = el.querySelectorAll ? el.querySelectorAll('.price[data-price-val]') : [];
        if (el.matches && el.matches('.price[data-price-val]')) {
          animatePriceCount(el);
        }
        prices.forEach(p => animatePriceCount(p));

        // Giải phóng will-change sau khi animation kết thúc
        setTimeout(() => {
          el.style.willChange = 'auto';
        }, 1100 + delay);
      }
    });
  }, {
    threshold: 0.12,
    rootMargin: '0px 0px -10% 0px'
  });

  revealElements.forEach(el => observer.observe(el));
}

// Hàm đếm số giá tiền từ 0 đến giá trị thực trong 900ms
function animatePriceCount(priceEl) {
  const raw = priceEl.getAttribute('data-price-val');
  if (!raw) return;
  const target = parseFloat(raw);
  if (isNaN(target)) return;

  const duration = 900;
  const startTime = performance.now();
  const formatter = new Intl.NumberFormat('vi-VN');

  function step(now) {
    const elapsed = now - startTime;
    const progress = Math.min(1, elapsed / duration);
    // Ease-out quad nảy nhẹ
    const ease = 1 - (1 - progress) * (1 - progress);
    const current = Math.round(target * ease);
    priceEl.textContent = `${formatter.format(current)} ₫`;
    if (progress < 1) {
      requestAnimationFrame(step);
    } else {
      priceEl.textContent = `${formatter.format(target)} ₫`;
    }
  }
  requestAnimationFrame(step);
}
