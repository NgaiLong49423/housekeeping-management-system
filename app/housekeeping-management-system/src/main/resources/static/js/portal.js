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
      remove.addEventListener("click", () => { days.delete(date); render(); });
      chip.append(label, remove); selectedDates.append(chip);
    });
  };
  const renderAvailableDates = () => {
    const previousScroll = calendarDates.scrollLeft;
    calendarDates.replaceChildren();
    preferredStaffWrap.hidden = preference !== "staff";
    if (!selectedServices().length) { calendarStatus.textContent = "Chọn dịch vụ để xem ngày có nhân viên làm."; updateDateNavigation(); return; }
    if (preference === "staff" && !preferredStaff.value) { calendarStatus.textContent = "Chọn nhân viên trước; chỉ các ngày người đó có giờ phù hợp mới hiện."; updateDateNavigation(); return; }
    if (calendarPending) { calendarStatus.textContent = "Đang kiểm tra ngày và lịch nhân viên…"; updateDateNavigation(); return; }
    if (calendarError) {
      calendarStatus.textContent=calendarError;
      const retry=document.createElement("button"); retry.type="button"; retry.className="btn btn-sm btn-outline-primary ms-2"; retry.textContent="Tải lại ngày";
      retry.addEventListener("click",loadAvailableDates,{once:true}); calendarStatus.append(retry); updateDateNavigation(); return;
    }
    calendarStatus.textContent = calendarNotice || (availableDates.length
      ? "Chỉ các ngày còn mốc giờ hợp lệ mới được hiển thị. Chọn một hoặc nhiều ngày."
      : "Không có ngày phù hợp trong 30 ngày tới.");
    availableDates.forEach(date => {
      const button = document.createElement("button"); button.type = "button"; button.className = "booking-available-date";
      const selected = days.has(date); button.classList.toggle("selected",selected); button.setAttribute("aria-pressed",String(selected));
      const weekday = document.createElement("strong"); weekday.textContent = new Date(`${date}T12:00:00`).toLocaleDateString("vi-VN",{weekday:"long"});
      const dayLabel = document.createElement("span"); dayLabel.textContent = new Date(`${date}T12:00:00`).toLocaleDateString("vi-VN",{day:"2-digit",month:"2-digit",year:"numeric"});
      button.append(weekday,dayLabel);
      button.addEventListener("click",()=>toggleDate(date)); calendarDates.append(button);
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
      removed.forEach(date=>days.delete(date));
      if(removed.length)calendarNotice=`Đã bỏ ${removed.length} ngày vì không còn giờ hoặc nhân viên phù hợp với lựa chọn hiện tại.`;
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
    if(days.has(date))loadDay(days.get(date));
  }
  async function loadDay(day) {
    const chosen = selectedServices();
    if (!chosen.length) { day.options = null; day.loading = false; day.error = "Chọn ít nhất một dịch vụ để tra lịch."; render(); return; }
    const request = ++sequence; day.request = request; day.loading = true; day.error = ""; pending++; render();
    const query = new URLSearchParams({ workDate: day.date });
    chosen.forEach(input => query.append("serviceIds", input.value));
    if (day.staffId) query.set("staffId", day.staffId);
    if (day.time) query.set("startTime", day.time);
    try {
      const response = await fetch(`/customer/bookings/availability-options?${query}`, { headers: { Accept: "application/json" } });
      if (!response.ok) throw new Error("Không tải được lịch khả dụng cho ngày này.");
      const options = await response.json();
      if (day.request !== request) return;
      day.options = options; day.error = "";
      day.timeOptions = options.timeOptions || [];
      if (day.time && !day.timeOptions.some(option => option.startTime === day.time)) {
        day.time = ""; day.staffId = "";
        day.notice = `Lựa chọn trước không còn phù hợp. ${options.message || "Hãy chọn lại giờ và nhân viên."}`;
      }
      if (day.staffId && day.time) {
        const eligible = timeFor(day, day.time)?.staffOptions || [];
        if (!eligible.some(person => String(person.id) === String(day.staffId))) {
          day.staffId = "";
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
  const makeStaffSelect = (day, available) => {
    const select = document.createElement("select"); select.className = "form-select booking-day-staff";
    select.append(new Option(preference === "staff" ? "Chọn nhân viên" : "Chọn giờ trước để xem nhân viên", ""));
    available.forEach(person => select.add(new Option(person.name, person.id)));
    select.value = day.staffId || "";
    select.disabled = day.loading || !selectedServices().length || (preference === "time" && !day.time);
    select.addEventListener("change", () => {
      day.staffId = select.value;
      if (preference === "staff" || !timeFor(day, day.time)?.staffOptions.some(person => String(person.id) === String(day.staffId))) day.time = "";
      if (preference === "time" && day.time && day.staffId) day.expanded = false;
      day.notice = ""; loadDay(day);
    });
    return select;
  };
  const renderDay = day => {
    const card = document.createElement("article"); card.className = "booking-day-card"; card.dataset.bookingDate = day.date;
    const head = document.createElement("header"); head.className = "booking-day-card-heading";
    const title = document.createElement("strong"); title.textContent = weekdayDate(day.date);
    const actions = document.createElement("div"); actions.className = "booking-day-card-actions";
    const edit = document.createElement("button"); edit.type = "button"; edit.className = "btn btn-sm btn-outline-primary";
    const selectedStaffName = allStaff.find(person => String(person.id) === String(day.staffId))?.name;
    edit.textContent = day.expanded ? "Thu gọn" : (day.time && day.staffId ? `${day.time} · ${selectedStaffName || "Đã chọn"} · Sửa` : "Chọn giờ và nhân viên");
    edit.setAttribute("aria-expanded", String(Boolean(day.expanded)));
    edit.addEventListener("click", () => { days.forEach(item => { item.expanded = item === day ? !day.expanded : false; }); render(); });
    const remove = document.createElement("button"); remove.type = "button"; remove.className = "btn btn-sm btn-outline-danger"; remove.textContent = "Bỏ ngày";
    remove.addEventListener("click", () => { days.delete(day.date); render(); });
    actions.append(edit, remove); head.append(title, actions); card.append(head);
    const status = document.createElement("p"); status.className = "small text-secondary booking-day-status";
    status.setAttribute("role", "status");
    status.textContent = day.loading ? "Đang tải lịch…" : day.error || day.notice || day.options?.message || (day.time && day.staffId ? "Đã chọn giờ và nhân viên cho ngày này." : "Chọn giờ và nhân viên cho ngày này.");
    card.append(status);
    addHiddenSelection(card, day);
    if (!day.expanded) return card;
    const eligibleStaff = day.time ? (timeFor(day, day.time)?.staffOptions || [])
      : [...new Map((day.timeOptions || []).flatMap(option => option.staffOptions || []).map(person => [String(person.id), person])).values()];
    if (preference === "staff") {
      if (!day.staffId) {
        const prompt = document.createElement("p"); prompt.className = "small text-secondary mb-0"; prompt.textContent = "Chọn nhân viên ở phía trên để xem các giờ người đó có thể làm."; card.append(prompt);
      }
    } else if (day.time) {
      const staffLabel = document.createElement("label"); staffLabel.className = "booking-day-field";
      const staffCaption = document.createElement("span"); staffCaption.textContent = "Nhân viên phù hợp với giờ đã chọn";
      staffLabel.append(staffCaption, makeStaffSelect(day, eligibleStaff)); card.append(staffLabel);
    }
    if (preference === "time") {
      const pickerLabel = document.createElement("label"); pickerLabel.className = "booking-day-field booking-day-time-field";
      const pickerCaption = document.createElement("span"); pickerCaption.textContent = "Giờ bắt đầu";
      const picker = document.createElement("div"); picker.className = "time-picker"; picker.dataset.quarterTime = ""; picker.dataset.bookingTimePicker = ""; picker.dataset.changeOnDone = "";
      const trigger = document.createElement("input"); trigger.type = "text"; trigger.className = "form-control time-trigger"; trigger.dataset.timeTrigger = ""; trigger.readOnly = true; trigger.disabled = day.loading || !day.options || !selectedServices().length; trigger.placeholder = "Chọn giờ"; trigger.setAttribute("aria-label", `Giờ bắt đầu ${weekdayDate(day.date)}`);
      const value = document.createElement("input"); value.type = "hidden"; value.dataset.timeValue = ""; value.value = day.time || "";
      picker.append(trigger, value); pickerLabel.append(pickerCaption, picker); card.append(pickerLabel);
      if (window.initQuarterTimePicker) window.initQuarterTimePicker(picker);
      picker.setAllowedTimes?.((day.timeOptions || []).map(option => option.startTime));
      picker.addEventListener("change", () => { day.time = value.value; day.staffId = ""; day.notice = ""; render(); loadDay(day); });
    }
    if (preference === "staff" && day.staffId && !day.loading && day.options) {
      const list = document.createElement("div"); list.className = "booking-availability-options booking-time-only-options";
      (day.timeOptions || []).forEach(option => {
        const button = document.createElement("button"); button.type = "button"; button.className = "booking-availability-option";
        if (day.time === option.startTime) button.classList.add("selected");
        button.setAttribute("aria-pressed", String(day.time === option.startTime));
        const time = document.createElement("strong"); time.textContent = option.startTime;
        button.append(time);
        button.addEventListener("click", () => { day.time = option.startTime; day.notice = ""; day.expanded = false; render(); loadDay(day); });
        list.append(button);
      });
      if (!list.childElementCount) { const empty = document.createElement("p"); empty.className = "small text-secondary mb-0"; empty.textContent = day.options.message || "Nhân viên này chưa có giờ phù hợp trong ngày."; list.append(empty); }
      card.append(list);
    }
    return card;
  };
  function render() {
    renderDateChips(); renderAvailableDates(); daysContainer.replaceChildren();
    const ordered = [...days.values()].sort((a,b) => a.date.localeCompare(b.date));
    if (!ordered.length) { const empty = document.createElement("p"); empty.className = "small text-secondary mb-0"; empty.textContent = "Thêm ít nhất một ngày để xem lịch."; daysContainer.append(empty); }
    ordered.forEach(day => daysContainer.append(renderDay(day)));
    refresh();
  }
  preferredStaff.value=requestedStaffId||"";
  preferredStaff.addEventListener("change",()=>{
    days.forEach(day=>{day.time="";day.staffId=preferredStaff.value;day.notice="";});
    render(); loadAvailableDates(); days.forEach(loadDay);
  });
  preferenceInputs.forEach(input => input.addEventListener("change", () => {
    preference=input.value; days.forEach(day=>{day.time="";day.staffId=preference==="staff"?preferredStaff.value:"";day.notice="";});
    if(preference==="staff"&&!preferredStaff.value)days.clear();
    render(); loadAvailableDates(); days.forEach(loadDay);
  }));
  services.forEach(input => input.addEventListener("change", () => {
    days.forEach(day=>{day.time="";day.staffId=preference==="staff"?preferredStaff.value:"";day.notice="";});
    render(); loadAvailableDates(); days.forEach(loadDay);
  }));
  house.addEventListener("change", refresh);
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
    const incomplete = selectedDays.filter(day => !day.options || !day.time || !day.staffId || !timeFor(day, day.time)?.staffOptions.some(person => String(person.id) === String(day.staffId)));
    let error = "";
    if (!selected.length) error = "Chọn ít nhất một dịch vụ.";
    else if (!house.value) error = "Chọn địa chỉ nhà cần dọn.";
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
    if (submit.disabled || !bookingForm.reportValidity()) event.preventDefault();
  });
  render(); loadAvailableDates();
}
