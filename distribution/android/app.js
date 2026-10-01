'use strict';
(async function () {
  const button = document.getElementById('download-button');
  try {
    const response = await fetch('/release.json', { cache: 'no-store' });
    if (!response.ok) throw new Error('Manifest unavailable');
    const release = await response.json();
    if (release.status !== 'available') return;
    const url = new URL(release.downloadUrl);
    const source = new URL(release.releaseUrl);
    const repo = '/jordelmir/MEET-Mecanicos-Especialistas-En-Todo/releases/';
    if (url.protocol !== 'https:' || url.hostname !== 'github.com' || !url.pathname.startsWith(repo + 'download/') || !url.pathname.endsWith('.apk') || source.protocol !== 'https:' || source.hostname !== 'github.com' || !source.pathname.startsWith(repo + 'tag/') || !/^[a-f0-9]{64}$/.test(release.sha256) || !release.version || !release.fileName || !Number.isSafeInteger(release.sizeBytes) || release.sizeBytes <= 0) throw new Error('Invalid release manifest');
    button.href = url.href;
    button.removeAttribute('aria-disabled');
    button.removeAttribute('tabindex');
    document.getElementById('release-summary').textContent = `${release.version} · ${(release.sizeBytes / 1024 / 1024).toFixed(1)} MB · APK para Android`;
    document.getElementById('download-note').textContent = 'Descarga directa desde el release publicado en GitHub. Revisa la versión antes de instalar; Android verificará la compatibilidad de la instalación.';
    for (const [key, value] of Object.entries({ version: release.version, file: release.fileName, size: `${(release.sizeBytes / 1024 / 1024).toFixed(1)} MB`, hash: release.sha256 })) document.getElementById(`release-${key}`).textContent = value;
    document.getElementById('release-source').href = source.href;
    document.getElementById('release-details').hidden = false;
  } catch (_) {
    document.getElementById('release-summary').textContent = 'La descarga no está disponible en este momento.';
    document.getElementById('download-note').textContent = 'Vuelve a consultar esta página para obtener el archivo publicado.';
  }
})();
