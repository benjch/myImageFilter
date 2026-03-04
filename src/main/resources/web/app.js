const DEFAULT_START_PATH = 'C:\\Users\\NR5145\\HD_D\\benjch\\gitBenjch\\myScrapper\\cover';

const VIEWER_TOOLBAR_BASE_TEXT = 'Échap: retour mosaïque • ←/→ navigation • Suppr supprimer • 1/2/3/4 keep • Backspace/Échap (mosaïque): dossier parent';

const state = {
  currentPath: '',
  images: [],
  folders: [],
  entries: [],
  selectedIndex: 0,
  fullScreen: false,
  currentImageIndex: 0,
  keepDir: '',
  stretchMode: false
};

const grid = document.getElementById('grid');
const folderPathInput = document.getElementById('folderPathInput');
const openFolderBtn = document.getElementById('openFolderBtn');
const imageCount = document.getElementById('imageCount');
const viewer = document.getElementById('viewer');
const viewerImage = document.getElementById('viewerImage');
const viewerToolbar = document.getElementById('viewerToolbar');
const stretchToggleBtn = document.getElementById('stretchToggleBtn');
const toast = document.getElementById('toast');
const keepDirInput = document.getElementById('keepDirInput');
const keepActions = document.getElementById('keepActions');

folderPathInput.value = DEFAULT_START_PATH;

document.getElementById('saveKeepBtn').addEventListener('click', saveKeepDir);
openFolderBtn.addEventListener('click', () => openFolderFromInput().catch(handleError));
folderPathInput.addEventListener('keydown', (event) => {
  if (event.key === 'Enter') {
    openFolderFromInput().catch(handleError);
  }
});

document.addEventListener('keydown', onKeyDown);

if (keepActions) {
  keepActions.addEventListener('click', (event) => {
    const button = event.target.closest('button[data-keep-variant]');
    if (!button) return;
    keepCurrent(button.dataset.keepVariant).catch(handleError);
  });
}

if (stretchToggleBtn) {
  stretchToggleBtn.addEventListener('click', () => {
    setStretchMode(!state.stretchMode);
  });
}
setStretchMode(false);

grid.addEventListener('click', (event) => {
  const tile = event.target.closest('.tile');
  if (!tile) return;
  const index = Number(tile.dataset.index);
  select(index);
  openSelected();
});

async function init() {
  const cfg = await api('/api/config');
  state.keepDir = cfg.keepDir || '';
  keepDirInput.value = state.keepDir;
  await loadFolder(DEFAULT_START_PATH);
}

async function openFolderFromInput() {
  const path = folderPathInput.value.trim() || DEFAULT_START_PATH;
  closeViewer();
  await loadFolder(path);
}

async function loadFolder(path, preferredSelectedPath = null) {
  const data = await api(`/api/folder/entries?path=${encodeURIComponent(path)}`);
  state.currentPath = data.currentPath;
  state.images = data.images;
  state.folders = data.folders;
  state.entries = [
    ...state.images.map((x) => ({ ...x, type: 'image' })),
    ...state.folders.map((x) => ({ ...x, type: 'folder' }))
  ];
  if (preferredSelectedPath) {
    const preferredIndex = state.entries.findIndex((entry) => entry.path === preferredSelectedPath);
    state.selectedIndex = preferredIndex >= 0 ? preferredIndex : 0;
  } else {
    state.selectedIndex = 0;
  }
  render();
}

function focusGridNavigation() {
  const active = document.activeElement;
  if (active instanceof HTMLElement && (active.tagName === 'INPUT' || active.tagName === 'TEXTAREA')) {
    active.blur();
  }
  grid.focus({ preventScroll: true });
}

function render() {
  folderPathInput.value = state.currentPath || DEFAULT_START_PATH;
  imageCount.textContent = `${state.images.length} image(s)`;

  const fragment = document.createDocumentFragment();
  state.entries.forEach((entry, index) => {
    const tile = document.createElement('div');
    tile.className = `tile ${entry.type}` + (index === state.selectedIndex ? ' selected' : '');
    tile.dataset.index = index;

    if (entry.type === 'image') {
      const width = entry.width > 0 ? entry.width : '?';
      const height = entry.height > 0 ? entry.height : '?';
      const extension = (entry.extension || '').toUpperCase();
      tile.innerHTML = `
        <div class="thumb-frame">
          <img loading="lazy" decoding="async" src="/api/thumbnail?path=${encodeURIComponent(entry.path)}&size=360" alt="${entry.name}" />
        </div>
        <div class="tile-meta">${width}x${height}${extension ? `    ${extension}` : ''}</div>
        <div class="tile-name">${entry.name}</div>
      `;
    } else {
      tile.innerHTML = `<div class="folder-icon">📁</div><div class="tile-name">${entry.name}</div>`;
    }

    fragment.appendChild(tile);
  });
  grid.replaceChildren(fragment);

  ensureSelectedVisible();
  focusGridNavigation();
}

function select(index) {
  if (state.entries.length === 0) return;
  const clamped = Math.max(0, Math.min(state.entries.length - 1, index));
  state.selectedIndex = clamped;
  [...grid.children].forEach((el, i) => el.classList.toggle('selected', i === clamped));
  ensureSelectedVisible();
  focusGridNavigation();
}

function ensureSelectedVisible() {
  const selected = grid.children[state.selectedIndex];
  if (selected) {
    selected.scrollIntoView({ block: 'nearest', inline: 'nearest' });
  }
}

function currentEntry() {
  return state.entries[state.selectedIndex];
}

async function openSelected() {
  const entry = currentEntry();
  if (!entry) return;
  if (entry.type === 'folder') {
    await loadFolder(entry.path);
    return;
  }
  openFullscreenFromSelected();
}

function openFullscreenFromSelected() {
  const entry = currentEntry();
  if (!entry || entry.type !== 'image') return;
  state.fullScreen = true;
  state.currentImageIndex = state.images.findIndex((img) => img.path === entry.path);
  showCurrentImage();
  viewer.classList.remove('hidden');
  updateKeepActionsVisibility();
}

function setStretchMode(enabled) {
  state.stretchMode = enabled;
  viewerImage.classList.toggle('stretched', enabled);
  if (stretchToggleBtn) {
    stretchToggleBtn.setAttribute('aria-pressed', String(enabled));
    stretchToggleBtn.textContent = `Mode étiré: ${enabled ? 'ON' : 'OFF'}`;
  }
}

function showCurrentImage() {
  if (state.images.length === 0) {
    viewer.classList.add('hidden');
    state.fullScreen = false;
    if (viewerToolbar) viewerToolbar.textContent = VIEWER_TOOLBAR_BASE_TEXT;
    return;
  }
  state.currentImageIndex = Math.max(0, Math.min(state.images.length - 1, state.currentImageIndex));
  const img = state.images[state.currentImageIndex];
  viewerImage.src = `/api/image?path=${encodeURIComponent(img.path)}`;
  if (viewerToolbar) viewerToolbar.textContent = `${VIEWER_TOOLBAR_BASE_TEXT}\n${img.path}`;
}

function closeViewer() {
  state.fullScreen = false;
  viewer.classList.add('hidden');
  updateKeepActionsVisibility();
  if (viewerToolbar) viewerToolbar.textContent = VIEWER_TOOLBAR_BASE_TEXT;
}

function updateKeepActionsVisibility() {
  if (!keepActions) return;
  keepActions.classList.toggle('hidden', !state.fullScreen);
}

async function deleteCurrent() {
  const entry = state.fullScreen ? state.images[state.currentImageIndex] : currentEntry();
  if (!entry || entry.type === 'folder') {
    showToast('Action non disponible sur un dossier');
    return;
  }

  let mosaicPreferredPath = null;
  if (!state.fullScreen) {
    const currentImageIdx = state.images.findIndex((img) => img.path === entry.path);
    if (currentImageIdx >= 0) {
      if (currentImageIdx < state.images.length - 1) {
        mosaicPreferredPath = state.images[currentImageIdx + 1].path;
      } else if (currentImageIdx > 0) {
        mosaicPreferredPath = state.images[currentImageIdx - 1].path;
      }
    }
  }

  await api('/api/delete', 'POST', { path: entry.path });
  showToast(`Supprimé : ${entry.name}`);

  const deletedPath = entry.path;
  await loadFolder(state.currentPath, mosaicPreferredPath);

  if (state.fullScreen) {
    let newIndex = state.images.findIndex((i) => i.path === deletedPath);
    if (newIndex < 0) newIndex = Math.min(state.currentImageIndex, state.images.length - 1);
    state.currentImageIndex = newIndex;
    showCurrentImage();
  }
}

async function keepCurrent(variant = 'normal') {
  const entry = state.fullScreen ? state.images[state.currentImageIndex] : currentEntry();
  if (!entry || entry.type === 'folder') {
    showToast('Action non disponible sur un dossier');
    return;
  }

  const result = await api('/api/keep', 'POST', { path: entry.path, keepDir: state.keepDir, variant });
  showToast(`Copié dans Keep : ${result.filename}`);

  if (state.fullScreen) {
    if (state.currentImageIndex < state.images.length - 1) {
      state.currentImageIndex += 1;
      showCurrentImage();
    }
    return;
  }

  const currentIndex = state.selectedIndex;
  const nextIndex = currentIndex < state.entries.length - 1 ? currentIndex + 1 : Math.max(0, currentIndex - 1);
  select(nextIndex);
}

async function goParent() {
  if (!state.currentPath) return;
  const trimmed = state.currentPath.endsWith('/') && state.currentPath.length > 1
    ? state.currentPath.slice(0, -1)
    : state.currentPath;
  const slash = Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'));
  const parent = slash <= 0 ? trimmed.slice(0, 1) : trimmed.slice(0, slash);
  const previousPath = state.currentPath;
  closeViewer();
  await loadFolder(parent, previousPath);
}

async function saveKeepDir() {
  const keepDir = keepDirInput.value.trim();
  const result = await api('/api/config', 'POST', { keepDir });
  state.keepDir = result.keepDir || '';
  showToast('Dossier Keep enregistré');
}

function isBackNavigationKey(e) {
  return e.key === 'Backspace' || e.key === 'BrowserBack' || (e.altKey && e.key === 'ArrowLeft');
}

function onKeyDown(e) {
  if (state.fullScreen && (isBackNavigationKey(e) || e.key === 'Escape')) {
    e.preventDefault();
    e.stopPropagation();
    closeViewer();
    return;
  }

  if (!state.fullScreen && isBackNavigationKey(e)) {
    e.preventDefault();
    e.stopPropagation();
    goParent().catch(handleError);
    return;
  }

  if (!state.fullScreen && e.key === 'Escape') {
    e.preventDefault();
    e.stopPropagation();
    goParent().catch(handleError);
    return;
  }

  if (state.fullScreen) {
    if (e.key === 'ArrowLeft') {
      state.currentImageIndex--;
      showCurrentImage();
    } else if (e.key === 'ArrowRight') {
      state.currentImageIndex++;
      showCurrentImage();
    } else if (e.key === 'Escape') {
      closeViewer();
    } else if (e.key === 'Delete') {
      deleteCurrent().catch(handleError);
    } else if (e.key === '1') {
      keepCurrent('normal').catch(handleError);
    } else if (e.key === '2') {
      keepCurrent('back').catch(handleError);
    } else if (e.key === '3') {
      keepCurrent('instruction').catch(handleError);
    } else if (e.key === '4') {
      keepCurrent('divers').catch(handleError);
    }
    return;
  }

  if (e.key === 'ArrowLeft') {
    select(state.selectedIndex - 1);
  } else if (e.key === 'ArrowRight') {
    select(state.selectedIndex + 1);
  } else if (e.key === 'ArrowUp') {
    e.preventDefault();
    select(state.selectedIndex - gridColumnCount());
  } else if (e.key === 'ArrowDown') {
    e.preventDefault();
    select(state.selectedIndex + gridColumnCount());
  } else if (e.key === 'Enter') {
    openSelected().catch(handleError);
  } else if (e.key === 'Delete') {
    deleteCurrent().catch(handleError);
  } else if (e.key === '1') {
    keepCurrent('normal').catch(handleError);
  } else if (e.key === '2') {
    keepCurrent('back').catch(handleError);
  } else if (e.key === '3') {
    keepCurrent('instruction').catch(handleError);
  } else if (e.key === '4') {
    keepCurrent('divers').catch(handleError);
  }
}

function gridColumnCount() {
  const tiles = [...grid.children];
  if (tiles.length === 0) return 1;

  const top = tiles[0].offsetTop;
  const firstRowCount = tiles.findIndex((tile) => tile.offsetTop !== top);
  return firstRowCount === -1 ? tiles.length : firstRowCount;
}

function showToast(text) {
  toast.textContent = text;
  toast.classList.remove('hidden');
  clearTimeout(showToast.timer);
  showToast.timer = setTimeout(() => toast.classList.add('hidden'), 1800);
}

function handleError(error) {
  showToast(error.message || 'Erreur');
}

async function api(url, method = 'GET', body) {
  const options = { method, headers: {} };
  if (body) {
    options.headers['Content-Type'] = 'application/json';
    options.body = JSON.stringify(body);
  }
  const response = await fetch(url, options);
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(payload.error || `HTTP ${response.status}`);
  }
  return payload;
}

init().catch(handleError);
