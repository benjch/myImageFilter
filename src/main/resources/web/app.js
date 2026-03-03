const DEFAULT_START_PATH = 'C:\\Users\\NR5145\\HD_D\\benjch\\gitBenjch\\myScrapper\\cover\\n';

const state = {
  currentPath: '',
  images: [],
  folders: [],
  entries: [],
  selectedIndex: 0,
  fullScreen: false,
  currentImageIndex: 0,
  keepDir: ''
};

const grid = document.getElementById('grid');
const folderPathInput = document.getElementById('folderPathInput');
const openFolderBtn = document.getElementById('openFolderBtn');
const imageCount = document.getElementById('imageCount');
const viewer = document.getElementById('viewer');
const viewerImage = document.getElementById('viewerImage');
const toast = document.getElementById('toast');
const keepDirInput = document.getElementById('keepDirInput');

folderPathInput.value = DEFAULT_START_PATH;

document.getElementById('saveKeepBtn').addEventListener('click', saveKeepDir);
openFolderBtn.addEventListener('click', () => openFolderFromInput().catch(handleError));
folderPathInput.addEventListener('keydown', (event) => {
  if (event.key === 'Enter') {
    openFolderFromInput().catch(handleError);
  }
});

document.addEventListener('keydown', onKeyDown);

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

async function loadFolder(path) {
  const data = await api(`/api/folder/entries?path=${encodeURIComponent(path)}`);
  state.currentPath = data.currentPath;
  state.images = data.images;
  state.folders = data.folders;
  state.entries = [
    ...state.images.map((x) => ({ ...x, type: 'image' })),
    ...state.folders.map((x) => ({ ...x, type: 'folder' }))
  ];
  state.selectedIndex = 0;
  render();
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
}

function select(index) {
  if (state.entries.length === 0) return;
  const clamped = Math.max(0, Math.min(state.entries.length - 1, index));
  state.selectedIndex = clamped;
  [...grid.children].forEach((el, i) => el.classList.toggle('selected', i === clamped));
  ensureSelectedVisible();
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
}

function showCurrentImage() {
  if (state.images.length === 0) {
    viewer.classList.add('hidden');
    state.fullScreen = false;
    return;
  }
  state.currentImageIndex = Math.max(0, Math.min(state.images.length - 1, state.currentImageIndex));
  const img = state.images[state.currentImageIndex];
  viewerImage.src = `/api/image?path=${encodeURIComponent(img.path)}`;
}

function closeViewer() {
  state.fullScreen = false;
  viewer.classList.add('hidden');
}

async function deleteCurrent() {
  const entry = state.fullScreen ? state.images[state.currentImageIndex] : currentEntry();
  if (!entry || entry.type === 'folder') {
    showToast('Action non disponible sur un dossier');
    return;
  }
  await api('/api/delete', 'POST', { path: entry.path });
  showToast(`Supprimé : ${entry.name}`);

  const deletedPath = entry.path;
  await loadFolder(state.currentPath);

  if (state.fullScreen) {
    let newIndex = state.images.findIndex((i) => i.path === deletedPath);
    if (newIndex < 0) newIndex = Math.min(state.currentImageIndex, state.images.length - 1);
    state.currentImageIndex = newIndex;
    showCurrentImage();
  } else {
    select(Math.min(state.selectedIndex, state.entries.length - 1));
  }
}

async function keepCurrent() {
  const entry = state.fullScreen ? state.images[state.currentImageIndex] : currentEntry();
  if (!entry || entry.type === 'folder') {
    showToast('Action non disponible sur un dossier');
    return;
  }
  const result = await api('/api/keep', 'POST', { path: entry.path, keepDir: state.keepDir });
  showToast(`Copié dans Keep : ${result.filename}`);
}

function goParent() {
  if (!state.currentPath) return;
  const trimmed = state.currentPath.endsWith('/') && state.currentPath.length > 1
    ? state.currentPath.slice(0, -1)
    : state.currentPath;
  const slash = Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'));
  const parent = slash <= 0 ? trimmed.slice(0, 1) : trimmed.slice(0, slash);
  closeViewer();
  loadFolder(parent);
}

async function saveKeepDir() {
  const keepDir = keepDirInput.value.trim();
  const result = await api('/api/config', 'POST', { keepDir });
  state.keepDir = result.keepDir || '';
  showToast('Dossier Keep enregistré');
}

function onKeyDown(e) {
  if (e.key === 'Backspace') {
    e.preventDefault();
    goParent();
    return;
  }

  if (e.key === 'ArrowUp') {
    e.preventDefault();
    if (state.fullScreen) {
      closeViewer();
    } else {
      goParent();
    }
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
    } else if (e.key.toLowerCase() === 'd') {
      deleteCurrent().catch(handleError);
    } else if (e.key.toLowerCase() === 'k') {
      keepCurrent().catch(handleError);
    }
    return;
  }

  if (e.key === 'ArrowLeft') {
    select(state.selectedIndex - 1);
  } else if (e.key === 'ArrowRight') {
    select(state.selectedIndex + 1);
  } else if (e.key === 'Enter') {
    openSelected().catch(handleError);
  } else if (e.key.toLowerCase() === 'd') {
    deleteCurrent().catch(handleError);
  } else if (e.key.toLowerCase() === 'k') {
    keepCurrent().catch(handleError);
  }
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
