const API_BASE_URL = "http://localhost:8080/api/v1";
const THEME_KEY = "brasa_norte_theme";
const CHANNEL_NAME = "restaurante_channel";

const ROLE_VIEWS = {
  ROLE_MOZO: "waiter",
  ROLE_COCINERO: "kitchen",
  ROLE_ADMIN: "admin",
};

const STATUS = {
  NEW: "NUEVO",
  IN_PROGRESS: "EN PROCESO",
  READY: "LISTO",
  DELIVERED: "ENTREGADO",
};
const API_TO_VIEW_STATUS = { NUEVO: "NEW", PENDIENTE: "NEW", EN_PROCESO: "IN_PROGRESS", LISTO: "READY", ENTREGADO: "DELIVERED" };
const VIEW_TO_API_STATUS = { NEW: "PENDIENTE", IN_PROGRESS: "EN_PROCESO", READY: "LISTO", DELIVERED: "ENTREGADO" };

const FAVORITE_COLORS = ["#c94c24", "#d89a32", "#4e7356", "#427a9b", "#ad5a75", "#6d685f"];

const loginScreen = document.querySelector("#login-screen");
const appScreen = document.querySelector("#app-screen");
const workspace = document.querySelector("#workspace");
const roleLabel = document.querySelector("#user-role");
const toastRegion = document.querySelector("#toast-region");
const themeLabel = document.querySelector("[data-theme-label]");

let currentUser = null;
let apiToken = null;
let selectedTable = null;
let draft = [];
let isSendingOrder = false;
const pendingOrderUpdates = new Set();
let tables = [];
let menuProducts = [];
let kitchenSummary = null;
let roleLoadError = "";
let syncTimer = null;
let audioContext = null;
let state = { orders: [], updatedAt: 0 };
let channel = null;
let adminSales = [];
let adminSalesLoading = false;
let adminSalesError = "";
let adminSalesDateStart = "";
let adminSalesDateEnd = "";

if ("BroadcastChannel" in window) {
  channel = new BroadcastChannel(CHANNEL_NAME);
  channel.addEventListener("message", (event) => {
    if (event.data?.type === "BACKEND_ORDERS_CHANGED") void refreshCurrentRole(true);
  });
}

applyTheme(localStorage.getItem(THEME_KEY) || "light");
renderThemeLabel();

document.querySelector("[data-theme-toggle]").addEventListener("click", toggleTheme);
document.querySelector("#login-form").addEventListener("submit", handleLogin);
appScreen.addEventListener("click", handleAppClick);
appScreen.addEventListener("submit", handleAppSubmit);
window.addEventListener("storage", (event) => {
  if (event.key === THEME_KEY && event.newValue) {
    document.body.dataset.theme = event.newValue;
    renderThemeLabel();
  }
});

// Adaptador listo para conectar las operaciones locales con el backend REST.
async function apiRequest(path, options = {}) {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(apiToken ? { Authorization: `Bearer ${apiToken}` } : {}),
      ...options.headers,
    },
  });
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    const message = body?.message || body?.error || `Error de API: ${response.status}`;
    if (response.status === 401 && currentUser) {
      logout();
      throw new Error("La sesión JWT venció o no es válida. Inicia sesión otra vez.");
    }
    if (response.status === 403) throw new Error("Tu rol no tiene permiso para realizar esta acción.");
    throw new Error(message);
  }
  if (response.status === 204) return null;
  return response.json();
}

function applyTheme(theme) {
  document.body.dataset.theme = theme;
  localStorage.setItem(THEME_KEY, theme);
  renderThemeLabel();
}

function toggleTheme() {
  applyTheme(document.body.dataset.theme === "dark" ? "light" : "dark");
}

function renderThemeLabel() {
  if (themeLabel) {
    themeLabel.textContent = document.body.dataset.theme === "dark" ? "Modo claro" : "Modo oscuro";
  }
}

async function handleLogin(event) {
  event.preventDefault();
  const input = document.querySelector("#pin-input");
  const error = document.querySelector("#login-error");
  const pin = input.value.trim();

  if (!/^\d{4}$/.test(pin)) {
    error.textContent = "Introduce un PIN válido de 4 dígitos.";
    input.select();
    return;
  }

  const submitButton = document.querySelector("#login-form button[type='submit']");
  submitButton.disabled = true;
  error.textContent = "Verificando acceso...";
  try {
    const authenticated = await apiRequest("/auth/login", {
      method: "POST",
      body: JSON.stringify({ pin }),
    });
    const role = ROLE_VIEWS[authenticated.rol];
    if (!role) throw new Error("El usuario no tiene un rol habilitado para esta aplicación.");

    apiToken = authenticated.token;
    currentUser = { id: authenticated.usuarioId, name: authenticated.nombre, role, apiRole: authenticated.rol };
    selectedTable = null;
    draft = [];
    adminSales = [];
    adminSalesDateStart = "";
    adminSalesDateEnd = "";
    error.textContent = "";
    primeAudio();
    loginScreen.hidden = true;
    appScreen.hidden = false;
    roleLabel.textContent = currentUser.name;
    render();
    startRoleSync();
    if (role === "waiter") void loadWaiterData();
    if (role === "kitchen") void loadKitchenData();
    if (role === "admin") void loadAdminSales();
  } catch (loginError) {
    error.textContent = loginError instanceof TypeError
      ? "No se pudo conectar con el servidor. Comprueba que la API esté activa."
      : loginError.message || "PIN inválido.";
    input.select();
  } finally {
    submitButton.disabled = false;
  }
}

function primeAudio() {
  const AudioContextClass = window.AudioContext || window.webkitAudioContext;
  if (!AudioContextClass) return;
  try {
    audioContext = new AudioContextClass();
    audioContext.resume();
  } catch (error) {
    audioContext = null;
  }
}

function playReadySound() {
  if (!audioContext || audioContext.state === "closed") primeAudio();
  if (!audioContext) return;
  const now = audioContext.currentTime;
  [0, 0.2].forEach((offset) => {
    const oscillator = audioContext.createOscillator();
    const gain = audioContext.createGain();
    oscillator.type = "sine";
    oscillator.frequency.value = offset ? 880 : 660;
    gain.gain.setValueAtTime(0.0001, now + offset);
    gain.gain.exponentialRampToValueAtTime(0.18, now + offset + 0.02);
    gain.gain.exponentialRampToValueAtTime(0.0001, now + offset + 0.18);
    oscillator.connect(gain);
    gain.connect(audioContext.destination);
    oscillator.start(now + offset);
    oscillator.stop(now + offset + 0.2);
  });
}

function handleAppClick(event) {
  const button = event.target.closest("[data-action]");
  if (!button) return;

  const { action } = button.dataset;
  if (action === "logout") logout();
  if (action === "select-table") selectTable(Number(button.dataset.table));
  if (action === "add-item") addItem(button.dataset.item);
  if (action === "remove-item") removeItem(button.dataset.item);
  if (action === "send-order") sendOrder();
  if (action === "set-status") setOrderStatus(button.dataset.order, button.dataset.status);
  if (action === "deliver-order") setOrderStatus(button.dataset.order, "DELIVERED");
  if (action === "load-role-data") void refreshCurrentRole();
  if (action === "load-admin-sales") void loadAdminSales();
  if (action === "clear-sales-filter") {
    adminSalesDateStart = "";
    adminSalesDateEnd = "";
    void loadAdminSales();
  }
  if (action === "download-report") void downloadReport();
}

function handleAppSubmit(event) {
  if (event.target.id !== "admin-sales-filter") return;
  event.preventDefault();
  const formData = new FormData(event.target);
  adminSalesDateStart = String(formData.get("fechaInicio") || "");
  adminSalesDateEnd = String(formData.get("fechaFin") || "");
  void loadAdminSales();
}

function logout() {
  currentUser = null;
  apiToken = null;
  draft = [];
  isSendingOrder = false;
  pendingOrderUpdates.clear();
  selectedTable = null;
  window.clearInterval(syncTimer);
  syncTimer = null;
  tables = [];
  menuProducts = [];
  kitchenSummary = null;
  roleLoadError = "";
  adminSales = [];
  adminSalesError = "";
  adminSalesLoading = false;
  appScreen.hidden = true;
  loginScreen.hidden = false;
  document.querySelector("#login-form").reset();
  document.querySelector("#login-error").textContent = "";
  document.querySelector("#pin-input").focus();
}

async function loadAdminSales() {
  if (currentUser?.role !== "admin") return;
  adminSalesLoading = true;
  adminSalesError = "";
  render();
  const parameters = new URLSearchParams();
  if (adminSalesDateStart) parameters.set("fechaInicio", adminSalesDateStart);
  if (adminSalesDateEnd) parameters.set("fechaFin", adminSalesDateEnd);
  const serializedParameters = parameters.toString();
  const query = serializedParameters ? `?${serializedParameters}` : "";
  try {
    const sales = await apiRequest(`/admin/ventas${query}`);
    adminSales = Array.isArray(sales) ? sales : [];
  } catch (error) {
    adminSalesError = error.message || "No se pudieron cargar las ventas.";
  } finally {
    adminSalesLoading = false;
    render();
  }
}

function selectTable(table) {
  selectedTable = table;
  draft = [];
  render();
}

function addItem(itemId) {
  const item = menuProducts.find((entry) => entry.id === itemId);
  if (!item) return;
  const existing = draft.find((entry) => entry.id === itemId);
  if (existing) existing.quantity += 1;
  else draft.push({ ...item, quantity: 1 });
  render();
}

function removeItem(itemId) {
  const item = draft.find((entry) => entry.id === itemId);
  if (!item) return;
  item.quantity -= 1;
  if (item.quantity <= 0) draft = draft.filter((entry) => entry.id !== itemId);
  render();
}

async function sendOrder() {
  if (isSendingOrder || !selectedTable || draft.length === 0) return;
  const table = tables.find((entry) => Number(entry.numeroMesa) === selectedTable);
  if (!table) {
    showToast("Mesa no disponible", "Actualiza las mesas e inténtalo de nuevo.");
    await loadWaiterData();
    return;
  }
  isSendingOrder = true;
  render();
  try {
    const order = await apiRequest("/mozo/pedidos", {
      method: "POST",
      body: JSON.stringify({
        mesaId: table.id,
        detalles: draft.map((item) => ({ productoId: item.productId, cantidad: item.quantity })),
      }),
    });
    draft = [];
    replaceVisibleOrders([order, ...state.orders.filter((entry) => String(entry.id) !== String(order.id))]);
    render();
    notifyBackendOrdersChanged();
    showToast("Comanda enviada", `Mesa ${selectedTable} · La cocina ya puede verla.`);
    await loadWaiterData();
  } catch (error) {
    showToast("No se guardó la comanda", error.message || "Comprueba la conexión con el servidor.");
  } finally {
    isSendingOrder = false;
    render();
  }
}

async function setOrderStatus(orderId, status) {
  const previous = state.orders.find((order) => String(order.id) === String(orderId));
  if (!previous || !Object.hasOwn(STATUS, status)) return;
  const orderKey = String(orderId);
  if (pendingOrderUpdates.has(orderKey)) return;
  pendingOrderUpdates.add(orderKey);
  render();
  const deliveredByWaiter = currentUser?.role === "waiter" && status === "DELIVERED";
  try {
    const updated = deliveredByWaiter
      ? await apiRequest(`/mozo/pedidos/${orderId}/estado`, { method: "PUT" })
      : await apiRequest(`/cocina/pedidos/${orderId}/estado`, {
        method: "PUT",
        body: JSON.stringify({ estado: VIEW_TO_API_STATUS[status] }),
      });
    replaceVisibleOrders([updated, ...state.orders.filter((order) => String(order.id) !== String(updated.id))]);
    render();
    notifyBackendOrdersChanged();
    if (status === "READY") {
      showToast("Pedido listo", `Mesa ${previous.table} · Ya puede retirarse.`);
      playReadySound();
    }
    if (currentUser.role === "waiter") await loadWaiterData();
    if (currentUser.role === "kitchen") await loadKitchenData();
  } catch (error) {
    showToast("No se actualizó el pedido", error.message || "Comprueba la conexión con el servidor.");
  } finally {
    pendingOrderUpdates.delete(orderKey);
    render();
  }
}

function render() {
  if (!currentUser) return;
  if (currentUser.role === "waiter") workspace.innerHTML = renderWaiter();
  if (currentUser.role === "kitchen") workspace.innerHTML = renderKitchen();
  if (currentUser.role === "admin") workspace.innerHTML = renderAdmin();
}

function renderWaiter() {
  const visibleTables = tables.length ? tables : Array.from({ length: 6 }, (_, index) => ({ numeroMesa: index + 1 }));
  const openOrders = state.orders.filter((order) => order.status !== "DELIVERED");
  return `
    <div class="dashboard-heading">
      <div><p class="eyebrow">Puesto de salón</p><h2>Hola, ${escapeHtml(currentUser.name)}</h2><p class="muted">Selecciona una mesa para tomar una comanda.</p></div>
      <span class="live-label">${openOrders.length} comandas activas</span>
    </div>
    <div class="waiter-layout">
      <div class="column-stack">
        <section class="surface" aria-labelledby="tables-title">
          <div class="surface__heading"><h3 id="tables-title">Mesas</h3><p>6 espacios · toca para seleccionar</p></div>
          <div class="table-grid">${visibleTables.map((tableEntry) => {
            const table = Number(tableEntry.numeroMesa);
            const tableOrders = openOrders.filter((order) => order.table === table);
            const selected = selectedTable === table ? " is-selected" : "";
            const busy = tableOrders.length ? " is-busy" : "";
            return `<button type="button" class="table-tile${selected}${busy}" data-action="select-table" data-table="${table}" aria-pressed="${selectedTable === table}"><span class="table-tile__number">${String(table).padStart(2, "0")}</span><span class="table-tile__meta">${tableOrders.length ? `${tableOrders.length} comanda${tableOrders.length > 1 ? "s" : ""}` : escapeHtml(tableEntry.estado || "Disponible")}</span></button>`;
          }).join("")}</div>
        </section>
        <section class="surface" aria-labelledby="orders-title">
          <div class="surface__heading"><h3 id="orders-title">Pedidos enviados</h3><p>Actualización en vivo</p></div>
          <div class="orders-list">${roleLoadError ? `<div class="empty-state empty-state--error">${escapeHtml(roleLoadError)} <button type="button" class="button button--outline button--small" data-action="load-role-data">Reintentar</button></div>` : renderWaiterOrders()}</div>
        </section>
      </div>
      <section class="surface" aria-labelledby="menu-title">
        <div class="surface__heading"><h3 id="menu-title">Nueva comanda</h3><p>${selectedTable ? `Mesa ${selectedTable}` : "Selecciona mesa"}</p></div>
        <div class="order-builder">${selectedTable ? renderOrderBuilder() : `<div class="order-builder__empty">Elige una mesa del croquis<br>para empezar el pedido.</div>`}</div>
      </section>
    </div>`;
}

function renderOrderBuilder() {
  const total = draft.reduce((sum, item) => sum + item.price * item.quantity, 0);
  return `<p class="order-builder__table">Mesa ${selectedTable} · Menú</p>
    <div class="menu-list">${menuProducts.map((item) => `<div class="menu-row"><div><span class="menu-row__name">${escapeHtml(item.name)}</span><span class="menu-row__price">${formatCurrency(item.price)}</span></div><button type="button" class="add-button" data-action="add-item" data-item="${escapeHtml(item.id)}" aria-label="Agregar ${escapeHtml(item.name)}">+</button></div>`).join("")}</div>
    ${draft.length ? `<div class="draft-list">${draft.map((item) => `<div class="draft-line"><span>${item.quantity} × ${escapeHtml(item.name)}</span><span>${formatCurrency(item.price * item.quantity)} <button type="button" data-action="remove-item" data-item="${item.id}" aria-label="Quitar una unidad de ${escapeHtml(item.name)}">−</button></span></div>`).join("")}</div>` : ""}
    <div class="order-builder__footer"><span class="order-builder__total">${formatCurrency(total)}</span><button type="button" class="button button--primary" data-action="send-order" ${draft.length && !isSendingOrder ? "" : "disabled"}>${isSendingOrder ? "Enviando..." : "Enviar a cocina"}</button></div>`;
}

function renderWaiterOrders() {
  const orders = state.orders.filter((order) => order.status !== "DELIVERED").sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  if (!orders.length) return `<div class="empty-state">Todavía no hay pedidos enviados.</div>`;
  return orders.map((order) => {
    const updating = pendingOrderUpdates.has(String(order.id));
    const kitchenHint = order.status === "NEW"
      ? "Esperando que cocina marque el pedido como listo."
      : order.status === "IN_PROGRESS"
        ? "Cocina está preparando este pedido."
        : "";
    return `<article class="order-card"><div class="order-card__top"><div><p class="order-card__title">Mesa ${order.table} · ${order.items.reduce((sum, item) => sum + item.quantity, 0)} platos</p><div class="order-card__time">${formatTime(order.createdAt)}</div></div>${statusBadge(order.status)}</div><p class="order-card__items">${renderItemsText(order.items)}</p>${kitchenHint ? `<p class="order-card__hint">${kitchenHint}</p>` : ""}${order.status === "READY" ? `<div class="order-card__actions"><button type="button" class="button button--success button--small" data-action="deliver-order" data-order="${order.id}" ${updating ? "disabled" : ""}>${updating ? "Guardando..." : "Marcar entregado"}</button></div>` : ""}</article>`;
  }).join("");
}

function renderKitchen() {
  const activeOrders = state.orders.filter((order) => order.status === "NEW" || order.status === "IN_PROGRESS").sort((a, b) => a.createdAt.localeCompare(b.createdAt));
  const todayOrders = state.orders.filter((order) => isToday(order.createdAt));
  const dishesToday = kitchenSummary?.platosVendidos ?? todayOrders.reduce((sum, order) => sum + order.items.reduce((count, item) => count + item.quantity, 0), 0);
  const newCount = activeOrders.filter((order) => order.status === "NEW").length;
  return `
    <div class="dashboard-heading"><div><p class="eyebrow">Puesto de cocina</p><h2>Comandas</h2><p class="muted">Prioriza los pedidos nuevos y marca cada avance.</p></div><span class="live-label">${newCount} nuevos</span></div>
    ${roleLoadError ? `<div class="empty-state empty-state--error">${escapeHtml(roleLoadError)} <button type="button" class="button button--outline button--small" data-action="load-role-data">Reintentar</button></div>` : ""}
    <div class="kitchen-summary"><div class="stat"><span class="stat__label">Platos vendidos hoy</span><strong class="stat__value">${dishesToday}</strong><span class="stat__note">Unidades en comandas del día</span></div><div class="stat"><span class="stat__label">En cola</span><strong class="stat__value">${kitchenSummary?.comandasPendientes ?? activeOrders.length}</strong><span class="stat__note">Pedidos por preparar</span></div><div class="stat"><span class="stat__label">Listos hoy</span><strong class="stat__value">${kitchenSummary?.comandasListas ?? todayOrders.filter((order) => order.status === "READY" || order.status === "DELIVERED").length}</strong><span class="stat__note">Comandas terminadas</span></div></div>
    <div class="kitchen-board">${activeOrders.length ? activeOrders.map(renderKitchenTicket).join("") : `<div class="surface empty-state">Sin comandas pendientes. La cocina está al día.</div>`}</div>`;
}

function renderKitchenTicket(order) {
  const inProgress = order.status === "IN_PROGRESS";
  const updating = pendingOrderUpdates.has(String(order.id));
  return `<article class="kitchen-ticket${inProgress ? " is-progress" : ""}"><div class="kitchen-ticket__head"><div><h3>Mesa ${order.table}</h3><div class="order-card__time">Comanda · ${formatTime(order.createdAt)}</div></div>${statusBadge(order.status)}</div><ul class="kitchen-ticket__items">${order.items.map((item) => `<li><span>${escapeHtml(item.name)}</span><strong>× ${item.quantity}</strong></li>`).join("")}</ul><div class="kitchen-ticket__footer"><span class="order-builder__hint">${order.items.reduce((sum, item) => sum + item.quantity, 0)} platos</span><div class="order-card__actions">${!inProgress ? `<button type="button" class="button button--outline button--small" data-action="set-status" data-order="${order.id}" data-status="IN_PROGRESS" ${updating ? "disabled" : ""}>${updating ? "Guardando..." : "En proceso"}</button>` : ""}<button type="button" class="button button--success button--small" data-action="set-status" data-order="${order.id}" data-status="READY" ${updating ? "disabled" : ""}>${updating ? "Guardando..." : "Listo"}</button></div></div></article>`;
}

function renderAdmin() {
  const todaySales = adminSales.filter((sale) => isToday(sale.creadoEn));
  const weekSales = adminSales.filter((sale) => isThisWeek(sale.creadoEn));
  const monthSales = adminSales.filter((sale) => isThisMonth(sale.creadoEn));
  const activeFilter = adminSalesDateStart || adminSalesDateEnd;
  const rangeDescription = activeFilter
    ? `Rango consultado: ${adminSalesDateStart || "inicio"} a ${adminSalesDateEnd || "hoy"}`
    : "Histórico completo de ventas entregadas";
  const historyContent = adminSalesError
    ? `<div class="empty-state empty-state--error">${escapeHtml(adminSalesError)} <button class="button button--outline button--small" type="button" data-action="load-admin-sales">Reintentar</button></div>`
    : adminSalesLoading
      ? `<div class="empty-state">Cargando ventas de PostgreSQL...</div>`
      : adminSales.length
        ? `<div class="sales-table-wrap"><table class="sales-table"><thead><tr><th>Fecha</th><th>Pedido</th><th>Mesa</th><th>Detalle</th><th>Importe</th></tr></thead><tbody>${adminSales.map(renderAdminSaleRow).join("")}</tbody></table></div>`
        : `<div class="empty-state">No hay ventas entregadas para este rango.</div>`;
  return `
    <div class="dashboard-heading"><div><p class="eyebrow">Panel de gestión</p><h2>Ventas</h2><p class="muted">${escapeHtml(rangeDescription)}</p></div><span class="live-label">${adminSales.length} comandas entregadas</span></div>
    <section class="surface admin-panel">
      <div class="admin-panel__heading"><div><h3>Histórico de ventas</h3><p>Datos cargados desde PostgreSQL</p></div><div class="admin-actions"><button type="button" class="button button--quiet button--small" data-action="load-admin-sales" aria-label="Actualizar ventas" title="Actualizar ventas">↻</button><button type="button" class="button button--primary" data-action="download-report">Descargar Excel <span aria-hidden="true">↓</span></button></div></div>
      <form class="sales-filter" id="admin-sales-filter"><label>Desde<input type="date" name="fechaInicio" value="${escapeHtml(adminSalesDateStart)}"></label><label>Hasta<input type="date" name="fechaFin" value="${escapeHtml(adminSalesDateEnd)}"></label><button class="button button--outline" type="submit">Filtrar</button><button class="button button--quiet" type="button" data-action="clear-sales-filter">Ver todo</button></form>
      <div class="stats-grid sales-summary"><div class="stat"><span class="stat__label">Ventas hoy</span><strong class="stat__value">${formatCurrency(sumApiSales(todaySales))}</strong><span class="stat__note">${todaySales.length} comandas</span></div><div class="stat"><span class="stat__label">Esta semana</span><strong class="stat__value">${formatCurrency(sumApiSales(weekSales))}</strong><span class="stat__note">Desde el lunes</span></div><div class="stat"><span class="stat__label">Este mes</span><strong class="stat__value">${formatCurrency(sumApiSales(monthSales))}</strong><span class="stat__note">${monthSales.length} comandas</span></div></div>
      ${adminSalesError ? "" : `<div class="sales-charts"><section class="chart-panel"><div class="chart-heading"><h3>Ventas por mes</h3><span>Meses con ventas</span></div>${renderSalesChart(buildMonthlySales(adminSales), "month")}</section><section class="chart-panel"><div class="chart-heading"><h3>Ventas por día</h3><span>Días con ventas</span></div>${renderSalesChart(buildDailySales(adminSales), "day")}</section></div>`}
      ${adminSalesError ? "" : renderFavoriteDishes(adminSales)}
      <section class="sales-history"><div class="chart-heading"><h3>Comandas entregadas</h3><span>${adminSales.length} resultados</span></div>${historyContent}</section>
      <p class="report-note">Los gráficos y el historial reflejan las ventas entregadas devueltas por la API.</p>
    </section>`;
}

function renderAdminSaleRow(sale) {
  const date = new Date(sale.creadoEn);
  const details = (sale.detalles || []).map((item) => `${item.cantidad} × ${item.producto}`).join(" · ") || "Sin detalle";
  return `<tr><td>${new Intl.DateTimeFormat("es", { day: "2-digit", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" }).format(date)}</td><td>#${sale.id}</td><td>${sale.numeroMesa ?? "—"}</td><td>${escapeHtml(details)}</td><td>${formatCurrency(Number(sale.total) || 0)}</td></tr>`;
}

function sumApiSales(sales) {
  return sales.reduce((sum, sale) => sum + (Number(sale.total) || 0), 0);
}

function buildFavoriteDishes(sales) {
  const dishes = new Map();
  sales.forEach((sale) => {
    (sale.detalles || []).forEach((detail) => {
      const name = String(detail.producto || "").trim();
      const quantity = Math.max(0, Number(detail.cantidad) || 0);
      if (!name || !quantity) return;
      const key = detail.productoId ?? name;
      const dish = dishes.get(key) || { name, quantity: 0 };
      dish.quantity += quantity;
      dishes.set(key, dish);
    });
  });

  const ranked = [...dishes.values()].sort((first, second) => second.quantity - first.quantity);
  const favorites = ranked.slice(0, 5);
  if (ranked.length > favorites.length) {
    favorites.push({
      name: "Otros platos",
      quantity: ranked.slice(5).reduce((sum, dish) => sum + dish.quantity, 0),
    });
  }
  return favorites.map((dish, index) => ({ ...dish, color: FAVORITE_COLORS[index % FAVORITE_COLORS.length] }));
}

function renderFavoriteDishes(sales) {
  const favorites = buildFavoriteDishes(sales);
  const total = favorites.reduce((sum, dish) => sum + dish.quantity, 0);
  if (!total) {
    return `<section class="chart-panel favorites-panel"><div class="chart-heading"><h3>Platos favoritos</h3></div><div class="chart-empty">No hay platos vendidos en este rango.</div></section>`;
  }

  let accumulated = 0;
  const slices = favorites.map((dish) => {
    const start = accumulated;
    accumulated += (dish.quantity / total) * 100;
    return `${dish.color} ${start}% ${accumulated}%`;
  }).join(", ");
  const legend = favorites.map((dish) => {
    const share = ((dish.quantity / total) * 100).toLocaleString("es", { maximumFractionDigits: 1 });
    return `<li class="favorite-legend__item"><span class="favorite-legend__swatch" style="--favorite-color:${dish.color}"></span><span class="favorite-legend__name">${escapeHtml(dish.name)}</span><strong>${dish.quantity}</strong><span class="favorite-legend__share">${share}%</span></li>`;
  }).join("");

  return `<section class="chart-panel favorites-panel"><div class="chart-heading"><h3>Platos favoritos</h3><span>Por unidades vendidas · rango seleccionado</span></div><div class="favorites-layout"><div class="favorite-pie" role="img" aria-label="Participación de los platos más pedidos" style="--pie-slices:conic-gradient(${slices})"><div class="favorite-pie__center"><strong>${total}</strong><span>platos</span></div></div><ul class="favorite-legend">${legend}</ul></div></section>`;
}

function buildMonthlySales(sales) {
  const buckets = new Map();
  sales.forEach((sale) => {
    const date = new Date(sale.creadoEn);
    if (Number.isNaN(date.getTime())) return;
    const key = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}`;
    const bucket = buckets.get(key) || {
      label: new Intl.DateTimeFormat("es", { month: "short", year: "2-digit" }).format(date),
      total: 0,
    };
    bucket.total += Number(sale.total) || 0;
    buckets.set(key, bucket);
  });
  return [...buckets.entries()].sort(([monthA], [monthB]) => monthA.localeCompare(monthB)).map(([, bucket]) => bucket);
}

function buildDailySales(sales) {
  const buckets = new Map();
  sales.forEach((sale) => {
    const date = new Date(sale.creadoEn);
    if (Number.isNaN(date.getTime())) return;
    const key = formatDateKey(date);
    const bucket = buckets.get(key) || {
      label: new Intl.DateTimeFormat("es", { day: "numeric", month: "short" }).format(date),
      total: 0,
    };
    bucket.total += Number(sale.total) || 0;
    buckets.set(key, bucket);
  });
  return [...buckets.entries()].sort(([dateA], [dateB]) => dateA.localeCompare(dateB)).map(([, bucket]) => bucket);
}

function latestSalesDate(sales) {
  if (!sales.length) return new Date();
  return sales.reduce((latest, sale) => {
    const date = new Date(sale.creadoEn);
    return date > latest ? date : latest;
  }, new Date(sales[0].creadoEn));
}

function formatDateKey(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function renderSalesChart(points, period) {
  if (!points.length) return `<div class="chart-empty">Sin ventas en este periodo.</div>`;
  const max = Math.max(...points.map((point) => point.total), 0);
  const columns = points.map((point) => {
    const height = max ? Math.max(point.total > 0 ? 5 : 0, (point.total / max) * 100) : 0;
    const label = period === "month" ? point.label : point.label.replace(" de ", " ");
    return `<div class="chart-column" aria-label="${escapeHtml(point.label)}: ${formatCurrency(point.total)}"><span class="chart-value">${point.total ? formatCompactCurrency(point.total) : ""}</span><div class="chart-track"><span class="chart-bar" style="height:${height}%"></span></div><span class="chart-label">${escapeHtml(label)}</span></div>`;
  }).join("");
  const chart = `<div class="bar-chart bar-chart--${period}" role="img" aria-label="Gráfico de ventas ${period === "month" ? "por mes" : "por día"}">${columns}</div>`;
  if (period !== "day") return chart;
  const chartWidth = Math.max(points.length * 42, 1);
  return `<div class="chart-scroll"><div class="bar-chart bar-chart--day" style="grid-template-columns:repeat(${points.length},minmax(0,1fr));width:${chartWidth}px">${columns}</div></div>`;
}

function formatCompactCurrency(value) {
  return new Intl.NumberFormat("es-PE", { notation: "compact", maximumFractionDigits: 1 }).format(value);
}

async function downloadReport() {
  try {
    const response = await fetch(`${API_BASE_URL}/admin/reportes/excel`, {
      headers: { Authorization: `Bearer ${apiToken}` },
    });
    if (!response.ok) throw new Error(`No se pudo descargar el reporte (${response.status}).`);
    const blob = await response.blob();
    const link = document.createElement("a");
    const disposition = response.headers.get("Content-Disposition") || "";
    const filename = disposition.match(/filename="?([^";]+)"?/i)?.[1] || "ventas-restaurante.xlsx";
    link.href = URL.createObjectURL(blob);
    link.download = filename;
    link.click();
    window.setTimeout(() => URL.revokeObjectURL(link.href), 1000);
    showToast("Reporte generado", "Se descargó el archivo Excel de ventas.");
  } catch (error) {
    showToast("No se pudo descargar", error.message || "Comprueba la conexión con el servidor.");
  }
}

function showToast(title, message) {
  const toast = document.createElement("div");
  toast.className = "toast";
  toast.innerHTML = `<span class="toast__icon" aria-hidden="true">✓</span><div><p class="toast__title">${escapeHtml(title)}</p><p class="toast__message">${escapeHtml(message)}</p></div>`;
  toastRegion.append(toast);
  window.setTimeout(() => toast.remove(), 5000);
}

function statusBadge(status) {
  const classNames = { NEW: "new", IN_PROGRESS: "progress", READY: "ready", DELIVERED: "delivered" };
  return `<span class="status-badge status-badge--${classNames[status] || "new"}">${STATUS[status] || escapeHtml(status)}</span>`;
}

function renderItemsText(items) {
  return items.map((item) => `${item.quantity} × ${escapeHtml(item.name)}`).join(" · ");
}

function totalOrder(order) {
  return order.items.reduce((sum, item) => sum + item.price * item.quantity, 0);
}

function totalSales(orders) {
  return orders.reduce((sum, order) => sum + totalOrder(order), 0);
}

function isToday(value) {
  const date = new Date(value);
  const now = new Date();
  return date.getFullYear() === now.getFullYear() && date.getMonth() === now.getMonth() && date.getDate() === now.getDate();
}

function isThisWeek(value) {
  const date = new Date(value);
  const start = startOfWeek(new Date());
  const end = new Date(start);
  end.setDate(end.getDate() + 7);
  return date >= start && date < end;
}

function isThisMonth(value) {
  const date = new Date(value);
  const now = new Date();
  return date.getFullYear() === now.getFullYear() && date.getMonth() === now.getMonth();
}

function startOfWeek(date) {
  const start = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  const daysSinceMonday = (start.getDay() + 6) % 7;
  start.setDate(start.getDate() - daysSinceMonday);
  start.setHours(0, 0, 0, 0);
  return start;
}

function formatCurrency(value) {
  return new Intl.NumberFormat("es-PE", { style: "currency", currency: "PEN", maximumFractionDigits: 2 }).format(value);
}

function formatTime(value) {
  return new Intl.DateTimeFormat("es", { hour: "2-digit", minute: "2-digit" }).format(new Date(value));
}

function formatShortDate(value) {
  return new Intl.DateTimeFormat("es", { day: "2-digit", month: "short" }).format(value);
}

function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, (character) => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#39;",
  })[character]);
}

function startRoleSync() {
  window.clearInterval(syncTimer);
  const interval = currentUser?.role === "admin" ? 15000 : 5000;
  syncTimer = window.setInterval(() => void refreshCurrentRole(true), interval);
}

function refreshCurrentRole(notifyReady = false) {
  if (currentUser?.role === "waiter") return loadWaiterData(notifyReady);
  if (currentUser?.role === "kitchen") return loadKitchenData();
  if (currentUser?.role === "admin") return loadAdminSales();
}

function normalizeApiOrder(order) {
  return {
    id: String(order.id),
    mesaId: Number(order.mesaId),
    table: Number(order.numeroMesa),
    createdAt: order.creadoEn,
    status: API_TO_VIEW_STATUS[order.estado] || order.estado,
    total: Number(order.total) || 0,
    items: (order.detalles || []).map((detail) => ({
      id: String(detail.productoId),
      productId: Number(detail.productoId),
      name: detail.producto,
      quantity: Number(detail.cantidad) || 0,
      price: Number(detail.precioUnitario) || 0,
      subtotal: Number(detail.subtotal) || 0,
    })),
  };
}

function replaceVisibleOrders(apiOrders, notifyReady = false) {
  const oldStatuses = new Map(state.orders.map((order) => [String(order.id), order.status]));
  const orders = apiOrders.map(normalizeApiOrder);
  state = { ...state, orders, updatedAt: Date.now() };
  if (notifyReady && currentUser?.role === "waiter") {
    orders.filter((order) => order.status === "READY" && oldStatuses.get(order.id) !== "READY")
      .forEach((order) => {
        showToast("Pedido listo", `Mesa ${order.table} · ${renderItemsText(order.items)}.`);
        playReadySound();
      });
  }
}

async function loadWaiterData(notifyReady = false) {
  if (currentUser?.role !== "waiter") return;
  try {
    const [apiTables, products, orders] = await Promise.all([
      apiRequest("/mozo/mesas"),
      apiRequest("/mozo/productos"),
      apiRequest("/mozo/pedidos/estado"),
    ]);
    tables = apiTables;
    menuProducts = products.map((product) => ({
      id: String(product.id), productId: Number(product.id), name: product.nombre, price: Number(product.precio),
    }));
    replaceVisibleOrders(orders, notifyReady);
    roleLoadError = "";
  } catch (error) {
    roleLoadError = error.message || "No se pudieron cargar mesas y comandas.";
  }
  render();
}

async function loadKitchenData() {
  if (currentUser?.role !== "kitchen") return;
  try {
    const [orders, summary] = await Promise.all([
      apiRequest("/cocina/pedidos/pendientes"),
      apiRequest("/cocina/resumen"),
    ]);
    replaceVisibleOrders(orders);
    kitchenSummary = summary;
    roleLoadError = "";
  } catch (error) {
    roleLoadError = error.message || "No se pudieron cargar las comandas.";
  }
  render();
}

function notifyBackendOrdersChanged() {
  channel?.postMessage({ type: "BACKEND_ORDERS_CHANGED" });
}