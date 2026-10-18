"use strict";

// Progressive enhancement only. Identity, prices, ownership and business transitions are verified by Spring.
document.querySelectorAll("form[data-confirm]").forEach(form => {
  form.addEventListener("submit", event => {
    if (!window.confirm(form.dataset.confirm)) event.preventDefault();
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
  const staff = bookingForm.elements.staffId;
  const schedule = bookingForm.elements.scheduleId;
  const start = bookingForm.elements.startAt;
  const services = [...bookingForm.querySelectorAll("input[name=serviceIds]")];
  const intervals = [...bookingForm.querySelectorAll("[data-busy-interval]")];
  const summary = bookingForm.querySelector("[data-summary-services]");
  const message = bookingForm.querySelector("[data-booking-validation]");
  const currency = new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" });
  const asVietnamTime = value => new Date(`${value}+07:00`).getTime();
  const params = new URLSearchParams(window.location.search);
  if (!staff.value && [...staff.options].some(option => option.value === params.get("staffId"))) staff.value = params.get("staffId");
  if (!services.some(input => input.checked)) services.forEach(input => { input.checked = input.value === params.get("serviceId"); });

  function refresh() {
    [...schedule.options].forEach(option => {
      if (!option.value) return;
      const excluded = option.dataset.staff !== staff.value;
      option.hidden = excluded;
      option.disabled = excluded;
      if (excluded && option.selected) schedule.value = "";
    });
    intervals.forEach(interval => { interval.hidden = interval.dataset.staff !== staff.value; });
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
    bookingForm.querySelector("[data-summary-duration]").textContent = `${minutes} phút`;
    bookingForm.querySelector("[data-summary-price]").textContent = currency.format(price);
    const option = schedule.selectedOptions[0];
    start.min = option?.dataset.start || "";
    start.max = option?.dataset.end || "";
    let error = "";
    if (!selected.length) error = "Chọn ít nhất một dịch vụ.";
    if (schedule.value && start.value && selected.length) {
      const begin = asVietnamTime(start.value);
      const finish = begin + minutes * 60000;
      if (begin < asVietnamTime(option.dataset.start) || finish > asVietnamTime(option.dataset.end)) {
        error = "Tổng thời lượng phải nằm trọn trong khoảng sẵn sàng đã chọn.";
      } else if (intervals.some(interval => interval.dataset.staff === staff.value &&
          begin < Date.parse(interval.dataset.end) && finish > Date.parse(interval.dataset.start))) {
        error = "Khoảng thời gian này đã có lịch hẹn. Vui lòng chọn giờ khác.";
      }
    }
    // Attach the cross-field error to a visible required field, while allowing the server to recheck concurrent changes.
    start.setCustomValidity(error);
    message.textContent = error;
  }
  staff.addEventListener("change", refresh);
  schedule.addEventListener("change", () => {
    if (schedule.value) start.value = schedule.selectedOptions[0].dataset.start;
    refresh();
  });
  services.forEach(input => input.addEventListener("change", refresh));
  start.addEventListener("input", refresh);
  bookingForm.addEventListener("submit", event => {
    refresh();
    if (!bookingForm.reportValidity()) event.preventDefault();
  });
  refresh();
}
