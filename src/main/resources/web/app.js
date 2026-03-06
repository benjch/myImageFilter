const DEFAULT_START_PATH = 'C:\\Users\\NR5145\\HD_D\\benjch\\gitBenjch\\myScrapper\\cover';

const VIEWER_TOOLBAR_BASE_TEXT = 'Échap: retour mosaïque • ←/→ navigation • Suppr supprimer • 1/2/3/4 keep • Backspace/Échap (mosaïque): dossier parent';

const RESTORE_STORAGE_KEY = 'myImageFilter.uiState';

const state = {
  currentPath: '',
  images: [],
  folders: [],
  entries: [],
  selectedIndex: 0,
  fullScreen: false,
  currentImageIndex: 0,
  keepDir: '',
  stretchMode: false,
  thumbnailBust: 0,
  scrapInProgress: false
};

const grid = document.getElementById('grid');
const folderPathInput = document.getElementById('folderPathInput');
const openFolderBtn = document.getElementById('openFolderBtn');
const goParentBtn = document.getElementById('goParentBtn');
const loadImagesBtn = document.getElementById('loadImagesBtn');
const loadImageBtn = document.getElementById('loadImageBtn');
const refreshBtn = document.getElementById('refreshBtn');
const uploadBtn = document.getElementById('uploadBtn');
const getNameBtn = document.getElementById('getNameBtn');
const imageCount = document.getElementById('imageCount');
const viewer = document.getElementById('viewer');
const viewerImage = document.getElementById('viewerImage');
const viewerToolbar = document.getElementById('viewerToolbar');
const stretchToggleBtn = document.getElementById('stretchToggleBtn');
const mosaicModeBtn = document.getElementById('mosaicModeBtn');
const toast = document.getElementById('toast');
const keepDirInput = document.getElementById('keepDirInput');
const keepActions = document.getElementById('keepActions');
const googleSearchInput = document.getElementById('googleSearchInput');
const scrapGoogleBtn = document.getElementById('scrapGoogleBtn');
const scrapAllGoogleBtn = document.getElementById('scrapAllGoogleBtn');

folderPathInput.value = DEFAULT_START_PATH;

document.getElementById('saveKeepBtn').addEventListener('click', saveKeepDir);
if (scrapGoogleBtn) {
  scrapGoogleBtn.addEventListener('click', openGoogleImagesSearch);
}
if (scrapAllGoogleBtn) {
  scrapAllGoogleBtn.addEventListener('click', () => scrapGoogleQueryToCurrentFolder().catch(handleError));
}
if (googleSearchInput) {
  googleSearchInput.addEventListener('keydown', (event) => {
    if (event.key === 'Enter') {
      event.preventDefault();
      openGoogleImagesSearch();
    }
  });
}
openFolderBtn.addEventListener('click', () => openFolderFromInput().catch(handleError));
if (loadImagesBtn) {
  loadImagesBtn.addEventListener('click', () => importImagesFromClipboardHtml().catch(handleError));
}
if (loadImageBtn) {
  loadImageBtn.addEventListener('click', () => importSingleImageFromClipboard().catch(handleError));
}
if (refreshBtn) {
  refreshBtn.addEventListener('click', () => refreshCurrentFolder().catch(handleError));
}
if (uploadBtn) {
  uploadBtn.addEventListener('click', () => copySelectedImageToClipboard().catch(handleError));
}
if (getNameBtn) {
  getNameBtn.addEventListener('click', () => copySelectedImageNameToClipboard().catch(handleError));
}
if (goParentBtn) {
  goParentBtn.addEventListener('click', () => goParent().catch(handleError));
}
folderPathInput.addEventListener('keydown', (event) => {
  if (event.key === 'Enter') {
    openFolderFromInput().catch(handleError);
  }
});

document.addEventListener('keydown', onKeyDown);
window.addEventListener('beforeunload', persistUiState);
viewer.addEventListener('wheel', onViewerWheel, { passive: false });
viewer.addEventListener('click', onViewerClick);

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
if (mosaicModeBtn) {
  mosaicModeBtn.addEventListener('click', closeViewer);
}
setStretchMode(false);

grid.addEventListener('click', (event) => {
  const tile = event.target.closest('.tile');
  if (!tile) return;
  const index = Number(tile.dataset.index);
  select(index);
  openSelected();
});

function persistUiState() {
  const selectedEntry = currentEntry();
  const selectedImagePath = state.fullScreen
    ? state.images[state.currentImageIndex]?.path || null
    : (selectedEntry && selectedEntry.type === 'image' ? selectedEntry.path : null);

  const payload = {
    currentPath: state.currentPath || folderPathInput.value.trim() || DEFAULT_START_PATH,
    selectedPath: selectedEntry?.path || null,
    selectedImagePath,
    fullScreen: Boolean(state.fullScreen && selectedImagePath)
  };

  localStorage.setItem(RESTORE_STORAGE_KEY, JSON.stringify(payload));
  syncUrlWithUiState(payload);
}

function readPersistedUiState() {
  try {
    const raw = localStorage.getItem(RESTORE_STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    if (!parsed || typeof parsed !== 'object') return null;
    return parsed;
  } catch (_error) {
    return null;
  }
}

function readUiStateFromUrl() {
  const url = new URL(window.location.href);
  const currentPath = url.searchParams.get('path');
  const selectedPath = url.searchParams.get('selected');
  const selectedImagePath = url.searchParams.get('image');
  const fullScreen = url.searchParams.get('fullscreen') === '1';

  if (!currentPath) {
    return null;
  }

  return {
    currentPath,
    selectedPath,
    selectedImagePath,
    fullScreen
  };
}

function syncUrlWithUiState(payload) {
  const url = new URL(window.location.href);

  if (payload.currentPath) {
    url.searchParams.set('path', payload.currentPath);
  } else {
    url.searchParams.delete('path');
  }

  if (payload.selectedPath) {
    url.searchParams.set('selected', payload.selectedPath);
  } else {
    url.searchParams.delete('selected');
  }

  if (payload.selectedImagePath) {
    url.searchParams.set('image', payload.selectedImagePath);
  } else {
    url.searchParams.delete('image');
  }

  if (payload.fullScreen) {
    url.searchParams.set('fullscreen', '1');
  } else {
    url.searchParams.delete('fullscreen');
  }

  const nextUrl = `${url.pathname}${url.search}${url.hash}`;
  if (nextUrl !== `${window.location.pathname}${window.location.search}${window.location.hash}`) {
    window.history.replaceState(null, '', nextUrl);
  }
}

async function init() {
  const cfg = await api('/api/config');
  state.keepDir = cfg.keepDir || '';
  keepDirInput.value = state.keepDir;

  const persisted = readUiStateFromUrl() || readPersistedUiState();
  const startupPath = persisted?.currentPath || DEFAULT_START_PATH;
  const preferredPath = persisted?.selectedPath || persisted?.selectedImagePath || null;

  try {
    await loadFolder(startupPath, preferredPath);
  } catch (error) {
    if (startupPath !== DEFAULT_START_PATH) {
      await loadFolder(DEFAULT_START_PATH);
      showToast('Dossier restauré indisponible, retour au dossier par défaut');
    } else {
      throw error;
    }
  }

  if (persisted?.fullScreen && persisted?.selectedImagePath) {
    const restoredImageIndex = state.images.findIndex((img) => img.path === persisted.selectedImagePath);
    if (restoredImageIndex >= 0) {
      state.currentImageIndex = restoredImageIndex;
      state.fullScreen = true;
      showCurrentImage();
      viewer.classList.remove('hidden');
      updateKeepActionsVisibility();
    }
  }

  persistUiState();
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
  updateGoogleSearchSuggestion();
  persistUiState();
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
          <img loading="lazy" decoding="async" src="/api/thumbnail?path=${encodeURIComponent(entry.path)}&size=360&t=${state.thumbnailBust}" alt="${entry.name}" />
        </div>
        <div class="tile-meta">${width}x${height}${extension ? `    ${extension}` : ''}</div>
        <div class="tile-name">${entry.name}</div>
      `;
    } else {
      const imageCountLabel = Number.isFinite(entry.imageCount) ? entry.imageCount : 0;
      const isEmptyFolder = imageCountLabel === 0 || /\(\s*0\s*\)/.test(entry.name || '');
      if (isEmptyFolder) {
        tile.classList.add('empty-folder');
      }
      tile.innerHTML = `<div class="folder-icon">📁</div><div class="tile-name">${entry.name} (${imageCountLabel})</div>`;
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
  persistUiState();
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
  persistUiState();
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
    persistUiState();
    return;
  }
  state.currentImageIndex = Math.max(0, Math.min(state.images.length - 1, state.currentImageIndex));
  const img = state.images[state.currentImageIndex];
  viewerImage.src = `/api/image?path=${encodeURIComponent(img.path)}`;
  if (viewerToolbar) viewerToolbar.textContent = `${VIEWER_TOOLBAR_BASE_TEXT}\n${img.path}`;
  persistUiState();
}

function closeViewer() {
  state.fullScreen = false;
  viewer.classList.add('hidden');
  updateKeepActionsVisibility();
  if (viewerToolbar) viewerToolbar.textContent = VIEWER_TOOLBAR_BASE_TEXT;
  persistUiState();
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
  const sourcePath = folderPathInput.value.trim() || state.currentPath;
  if (!sourcePath) {
    showToast('Aucun dossier courant');
    return;
  }

  const parent = resolveParentPath(sourcePath);

  if (!parent || parent === sourcePath) {
    showToast('Déjà à la racine');
    return;
  }

  const previousPath = sourcePath;
  closeViewer();
  await loadFolder(parent, previousPath);
}

function resolveParentPath(path) {
  const trimmedPath = path.trim();
  if (!trimmedPath) {
    return null;
  }

  const preferBackslash = trimmedPath.includes('\\');
  const unixified = trimmedPath.replace(/\\/g, '/');

  const windowsDriveMatch = unixified.match(/^([A-Za-z]:)(?:\/|$)(.*)$/);
  if (windowsDriveMatch) {
    const drive = windowsDriveMatch[1];
    const rest = windowsDriveMatch[2].replace(/\/+$/, '');
    const segments = rest ? rest.split('/').filter(Boolean) : [];
    if (segments.length === 0) {
      return null;
    }
    segments.pop();
    const parentUnix = segments.length > 0 ? `${drive}/${segments.join('/')}` : `${drive}/`;
    return preferBackslash ? parentUnix.replace(/\//g, '\\') : parentUnix;
  }

  if (unixified.startsWith('//')) {
    const uncParts = unixified.split('/').filter(Boolean);
    if (uncParts.length < 3) {
      return null;
    }
    const root = `//${uncParts[0]}/${uncParts[1]}`;
    const tail = uncParts.slice(2, -1);
    const parentUnix = tail.length > 0 ? `${root}/${tail.join('/')}` : root;
    return preferBackslash ? parentUnix.replace(/\//g, '\\') : parentUnix;
  }

  const cleanUnix = unixified.length > 1 ? unixified.replace(/\/+$/, '') : unixified;
  if (cleanUnix === '/') {
    return null;
  }
  const slash = cleanUnix.lastIndexOf('/');
  if (slash < 0) {
    return null;
  }
  if (slash === 0) {
    return '/';
  }
  return cleanUnix.slice(0, slash);
}


async function refreshCurrentFolder() {
  if (!state.currentPath) {
    throw new Error('Aucun dossier courant');
  }
  const preferredPath = state.fullScreen
    ? state.images[state.currentImageIndex]?.path ?? null
    : currentEntry()?.path ?? null;
  await api('/api/thumbnail-cache/clear', 'POST');
  state.thumbnailBust = Date.now();
  await loadFolder(state.currentPath, preferredPath);
  showToast('Mosaïque rafraîchie (sans cache)');
}

async function saveKeepDir() {
  const keepDir = keepDirInput.value.trim();
  const result = await api('/api/config', 'POST', { keepDir });
  state.keepDir = result.keepDir || '';
  showToast('Dossier Keep enregistré');
}

async function importImagesFromClipboardHtml() {
  if (!navigator.clipboard?.readText) {
    throw new Error('Clipboard API texte indisponible');
  }
  if (!state.currentPath) {
    throw new Error('Aucun dossier courant');
  }

  const html = await navigator.clipboard.readText();
  if (!html || !html.trim()) {
    throw new Error('Le presse-papiers ne contient pas de HTML');
  }

  const result = await api('/api/import-from-html', 'POST', { folderPath: state.currentPath, html });
  await loadFolder(state.currentPath);
  if (result.importedCount > 0) {
    showToast(`${result.importedCount} image(s) importée(s)`);
    return;
  }
  showToast('Aucune image valide trouvée dans le HTML');
}

async function importSingleImageFromClipboard() {
  if (!navigator.clipboard?.read) {
    throw new Error('Clipboard API binaire indisponible');
  }
  if (!state.currentPath) {
    throw new Error('Aucun dossier courant');
  }

  const items = await navigator.clipboard.read();
  for (const item of items) {
    const imageType = item.types.find((type) => type.startsWith('image/'));
    if (!imageType) {
      continue;
    }

    const blob = await item.getType(imageType);
    const imageBase64 = await blobToBase64(blob);
    const result = await api('/api/import-image-from-clipboard', 'POST', {
      folderPath: state.currentPath,
      imageBase64,
      mimeType: imageType
    });
    await loadFolder(state.currentPath, result.path);
    showToast(`Image importée : ${result.filename}`);
    return;
  }

  throw new Error('Aucune image trouvée dans le presse-papiers');
}

function blobToBase64(blob) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const value = typeof reader.result === 'string' ? reader.result : '';
      const commaIndex = value.indexOf(',');
      if (commaIndex < 0) {
        reject(new Error('Conversion clipboard impossible'));
        return;
      }
      resolve(value.slice(commaIndex + 1));
    };
    reader.onerror = () => reject(new Error('Conversion clipboard impossible'));
    reader.readAsDataURL(blob);
  });
}



function removeDateParts(value) {
  if (!value) return '';

  let result = value;
  result = result.replace(/^(?:\d{8}|\d{4}[-_. ]\d{2}[-_. ]\d{2})\s*[-_. ]+\s*/i, '');
  result = result.replace(/\s*[-_. ]+\s*(?:\d{8}|\d{4}[-_. ]\d{2}[-_. ]\d{2})$/i, '');

  result = result
    .replace(/[._]{2,}/g, ' ')
    .replace(/\s{2,}/g, ' ')
    .replace(/^[._\-\s]+|[._\-\s]+$/g, '');

  return result;
}

function buildGoogleCoverQuery() {
  const sourcePath = folderPathInput.value.trim() || state.currentPath;
  const folderName = extractFolderName(sourcePath);
  const normalizedFolderName = removeDateParts(folderName.replace(/[_]+/g, ' ').trim());
  const cleanedFolderName = extractGameName(normalizedFolderName);
  const region = extractRegionName(normalizedFolderName);
  if (!cleanedFolderName) return 'cover';
  if (!region) return `${cleanedFolderName} cover`;
  return `${cleanedFolderName} ${region} cover`;
}

function updateGoogleSearchSuggestion() {
  if (!googleSearchInput) return;
  googleSearchInput.value = buildGoogleCoverQuery();
}

function openGoogleImagesSearch() {
  const defaultQuery = buildGoogleCoverQuery();
  const query = (googleSearchInput?.value || '').trim() || defaultQuery;
  if (!query) {
    showToast('Recherche Google vide');
    return;
  }

  if (googleSearchInput && !googleSearchInput.value.trim()) {
    googleSearchInput.value = query;
  }

  const url = `https://www.google.com/search?tbm=isch&q=${encodeURIComponent(query)}`;
  window.open(url, '_blank', 'noopener,noreferrer');
}

async function scrapGoogleQueryToCurrentFolder() {
  if (state.scrapInProgress) {
    showToast('Scrap déjà en cours');
    return;
  }

  const defaultQuery = buildGoogleCoverQuery();
  const query = (googleSearchInput?.value || '').trim() || defaultQuery;
  if (!query) {
    showToast('Recherche Google vide');
    return;
  }

  if (!state.currentPath) {
    showToast('Dossier courant introuvable');
    return;
  }

  if (googleSearchInput && !googleSearchInput.value.trim()) {
    googleSearchInput.value = query;
  }

  state.scrapInProgress = true;
  const stopScrapStatus = startScrapStatusNotification();
  if (scrapAllGoogleBtn) {
    scrapAllGoogleBtn.disabled = true;
  }

  try {
    const result = await api('/api/scrap-google-images', 'POST', {
      folderPath: state.currentPath,
      query,
      maxImages: 20
    });

    stopScrapStatus(result.importedCount || 0);
    showToast(`Scrap terminé: ${result.importedCount || 0} image(s) importée(s)`);
    await refreshCurrentFolder();
  } finally {
    if (scrapAllGoogleBtn) {
      scrapAllGoogleBtn.disabled = false;
    }
    state.scrapInProgress = false;
  }
}

function startScrapStatusNotification() {
  const startedAt = Date.now();
  let importedCount = 0;

  const renderStatus = () => {
    const elapsedSeconds = Math.max(0, Math.floor((Date.now() - startedAt) / 1000));
    showToast(`Scrapping en cours... ${importedCount} image(s) récupérée(s) • ${elapsedSeconds}s`, { persistent: true });
  };

  renderStatus();
  const timer = setInterval(renderStatus, 1000);

  return (finalImportedCount = importedCount) => {
    importedCount = finalImportedCount;
    clearInterval(timer);
    clearTimeout(showToast.timer);
    toast.classList.add('hidden');
  };
}

function extractFolderName(path) {
  if (!path) return '';
  const trimmed = path.trim().replace(/[\\/]+$/, '');
  if (!trimmed) return '';
  const normalized = trimmed.replace(/\\/g, '/');
  const segments = normalized.split('/').filter(Boolean);
  return segments.length ? segments[segments.length - 1] : '';
}

function extractRegionName(folderName) {
  if (!folderName) return '';

  const regionPattern = '(?:eu|eur|europe|us|usa|na|jp|jpn|japan|world|ww|pal|ntsc|fr|fra)';
  const regionMap = {
    eu: 'Europe',
    eur: 'Europe',
    europe: 'Europe',
    us: 'USA',
    usa: 'USA',
    na: 'USA',
    jp: 'Japan',
    jpn: 'Japan',
    japan: 'Japan',
    world: 'World',
    ww: 'World',
    pal: 'PAL',
    ntsc: 'NTSC',
    fr: 'France',
    fra: 'France'
  };

  const value = folderName
    .replace(/^\s+|\s+$/g, '')
    .replace(/^[._\-\s]+|[._\-\s]+$/g, '');

  if (!value) return '';

  const bracketPrefix = value.match(new RegExp(`^[[(]\\s*(${regionPattern})\\s*[)\\]]`, 'i'));
  const plainPrefix = value.match(new RegExp(`^(${regionPattern})\\s*[-_. ]+`, 'i'));
  const bracketSuffix = value.match(new RegExp(`[[(]\\s*(${regionPattern})\\s*[)\\]]\\s*$`, 'i'));
  const plainSuffix = value.match(new RegExp(`[-_. ]+(${regionPattern})\\s*$`, 'i'));

  const foundRegion = bracketPrefix?.[1] || plainPrefix?.[1] || bracketSuffix?.[1] || plainSuffix?.[1] || '';
  if (!foundRegion) return '';

  const key = foundRegion.toLowerCase();
  return regionMap[key] || foundRegion;
}

function extractGameName(folderName) {
  if (!folderName) return '';

  const regionPattern = '(?:eu|eur|europe|us|usa|na|jp|jpn|japan|world|ww|pal|ntsc|fr|fra)';

  let value = folderName
    .replace(/^\s+|\s+$/g, '')
    .replace(/^[._\-\s]+|[._\-\s]+$/g, '');

  if (!value) return '';

  value = value.replace(/^(?:\d{8}|\d{4}[-_. ]\d{2}[-_. ]\d{2})\s*[-_. ]+\s*/i, '');
  value = value.replace(/\s*[-_. ]+\s*(?:\d{8}|\d{4}[-_. ]\d{2}[-_. ]\d{2})$/i, '');

  value = value.replace(new RegExp(`\\s*[[(]\\s*${regionPattern}\\s*[)\\]]\\s*$`, 'i'), '');
  value = value.replace(new RegExp(`\\s*[-_. ]+\\s*${regionPattern}\\s*$`, 'i'), '');
  value = value.replace(new RegExp(`^[[(]\\s*${regionPattern}\\s*[)\\]]\\s*[-_. ]+\\s*`, 'i'), '');
  value = value.replace(new RegExp(`^${regionPattern}\\s*[-_. ]+\\s*`, 'i'), '');

  value = value
    .replace(/[._]{2,}/g, ' ')
    .replace(/\s{2,}/g, ' ')
    .replace(/^[._\-\s]+|[._\-\s]+$/g, '');

  return value || folderName;
}


async function copySelectedImageNameToClipboard() {
  if (!navigator.clipboard?.writeText) {
    throw new Error('Clipboard API texte indisponible');
  }

  const sourcePath = folderPathInput.value.trim() || state.currentPath;
  const folderName = extractFolderName(sourcePath);
  const gameName = extractGameName(folderName);
  if (!gameName) {
    throw new Error('Nom de dossier invalide');
  }

  await navigator.clipboard.writeText(gameName);
  showToast(`Nom copié dans le presse-papiers : ${gameName}`);
}

function blobToPngClipboardBlob(blob) {
  return new Promise((resolve, reject) => {
    const imageUrl = URL.createObjectURL(blob);
    const image = new Image();

    image.onload = () => {
      try {
        const canvas = document.createElement('canvas');
        canvas.width = image.naturalWidth || image.width;
        canvas.height = image.naturalHeight || image.height;
        const context = canvas.getContext('2d');
        if (!context) {
          reject(new Error('Canvas 2D indisponible'));
          return;
        }

        context.drawImage(image, 0, 0);
        canvas.toBlob((pngBlob) => {
          if (!pngBlob) {
            reject(new Error('Conversion PNG impossible'));
            return;
          }
          resolve(pngBlob);
        }, 'image/png');
      } catch (error) {
        reject(error);
      } finally {
        URL.revokeObjectURL(imageUrl);
      }
    };

    image.onerror = () => {
      URL.revokeObjectURL(imageUrl);
      reject(new Error('Chargement image impossible pour conversion PNG'));
    };

    image.src = imageUrl;
  });
}

async function writeImageBlobToClipboard(imageBlob) {
  const sourceMimeType = imageBlob.type || 'image/png';
  try {
    await navigator.clipboard.write([new ClipboardItem({ [sourceMimeType]: imageBlob })]);
    return;
  } catch (error) {
    if (!sourceMimeType.startsWith('image/')) {
      throw error;
    }
  }

  const pngBlob = await blobToPngClipboardBlob(imageBlob);
  await navigator.clipboard.write([new ClipboardItem({ 'image/png': pngBlob })]);
}

async function copySelectedImageToClipboard() {
  const entry = state.fullScreen ? state.images[state.currentImageIndex] : currentEntry();
  const isInvalidFullscreenEntry = state.fullScreen && (!entry || !entry.path);
  const isInvalidMosaicEntry = !state.fullScreen && (!entry || entry.type !== 'image');
  if (isInvalidFullscreenEntry || isInvalidMosaicEntry) {
    showToast(state.fullScreen ? 'Aucune image sélectionnée en plein écran' : 'Sélectionnez une image dans la mosaïque');
    return;
  }

  if (!navigator.clipboard?.write || typeof ClipboardItem === 'undefined') {
    throw new Error('Clipboard API image indisponible');
  }

  const response = await fetch(`/api/image?path=${encodeURIComponent(entry.path)}`);
  if (!response.ok) {
    throw new Error(`Impossible de charger l'image (HTTP ${response.status})`);
  }

  const imageBlob = await response.blob();
  await writeImageBlobToClipboard(imageBlob);
  showToast(`Image copiée dans le presse-papiers : ${entry.name}`);
}

function onViewerClick(event) {
  if (!state.fullScreen) return;
  if (event.target === viewerImage) {
    setStretchMode(!state.stretchMode);
    return;
  }
  if (event.target !== viewer) {
    return;
  }
  closeViewer();
}

function onViewerWheel(event) {
  if (!state.fullScreen) return;
  if (!state.images.length) return;

  event.preventDefault();
  const direction = event.deltaY > 0 ? 1 : -1;
  if (direction > 0 && state.currentImageIndex < state.images.length - 1) {
    state.currentImageIndex += 1;
    showCurrentImage();
  } else if (direction < 0 && state.currentImageIndex > 0) {
    state.currentImageIndex -= 1;
    showCurrentImage();
  }
}

function isBackNavigationKey(e) {
  return e.key === 'Backspace' || e.key === 'BrowserBack' || (e.altKey && e.key === 'ArrowLeft');
}

function isEditableElementActive() {
  const active = document.activeElement;
  if (!(active instanceof HTMLElement)) {
    return false;
  }
  return active.tagName === 'INPUT' || active.tagName === 'TEXTAREA' || active.isContentEditable;
}

function shouldIgnoreGlobalShortcut(event) {
  if (event.altKey || event.ctrlKey || event.metaKey) {
    return true;
  }
  return isEditableElementActive();
}

function onKeyDown(e) {
  if (!isEditableElementActive() && !e.ctrlKey && !e.altKey && !e.metaKey && e.key === 'F5') {
    e.preventDefault();
    refreshCurrentFolder().catch(handleError);
    return;
  }

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


  if (!state.fullScreen && !isEditableElementActive() && e.ctrlKey && !e.altKey && !e.metaKey) {
    const key = e.key.toLowerCase();
    if (key === 'c') {
      e.preventDefault();
      copySelectedImageToClipboard().catch(handleError);
      return;
    }
    if (key === 'v') {
      e.preventDefault();
      importSingleImageFromClipboard().catch(handleError);
      return;
    }
  }

  if (!state.fullScreen && !shouldIgnoreGlobalShortcut(e)) {
    if (e.key === 'l' || e.key === 'L') {
      e.preventDefault();
      importSingleImageFromClipboard().catch(handleError);
      return;
    }
    if (e.key === 'a' || e.key === 'A') {
      e.preventDefault();
      importImagesFromClipboardHtml().catch(handleError);
      return;
    }
    if (e.key === 'u' || e.key === 'U') {
      e.preventDefault();
      copySelectedImageToClipboard().catch(handleError);
      return;
    }
    if (e.key === 'n' || e.key === 'N') {
      e.preventDefault();
      copySelectedImageNameToClipboard().catch(handleError);
      return;
    }
  }

  if (state.fullScreen) {
    if (e.ctrlKey && !e.altKey && !e.metaKey && e.key.toLowerCase() === 'c') {
      e.preventDefault();
      e.stopPropagation();
      copySelectedImageToClipboard().catch(handleError);
      return;
    }

    if (e.key === 'ArrowLeft') {
      state.currentImageIndex--;
      showCurrentImage();
    } else if (e.key === 'ArrowRight') {
      state.currentImageIndex++;
      showCurrentImage();
    } else if (e.key === 'm' || e.key === 'M') {
      setStretchMode(!state.stretchMode);
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
    e.preventDefault();
    select(state.selectedIndex - 1);
  } else if (e.key === 'ArrowRight') {
    e.preventDefault();
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

function showToast(text, options = {}) {
  const { persistent = false, duration = 1800 } = options;
  toast.textContent = text;
  toast.classList.remove('hidden');
  clearTimeout(showToast.timer);
  if (!persistent) {
    showToast.timer = setTimeout(() => toast.classList.add('hidden'), duration);
  }
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
