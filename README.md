# myImageFilter — Trieur local de photos (mosaïque + plein écran)

Application web locale pour trier des photos rapidement au clavier (inspirée FastStone), sans cloud.

## Choix techniques

- **Backend : Java 21 + `HttpServer` natif JDK** (zéro framework lourd, simple à lancer en local).
- **Frontend : HTML/CSS/JS vanilla** (léger, aucune chaîne de build).
- **API JSON locale** pour explorer le disque, afficher images/miniatures, supprimer et garder.

## Fonctionnalités principales

- Navigation dans le disque via dossier courant + remontée parent (`↑`), avec racines accessibles au démarrage.
- Mosaïque du dossier courant avec ordre obligatoire :
  1. images,
  2. dossiers.
- Tuile sélectionnée avec liseré discret.
- Plein écran avec navigation `←/→`.
- `D` : suppression immédiate (corbeille si possible via `Desktop.moveToTrash`, sinon suppression directe).
- `K` : copie vers dossier Keep avec renommage automatique :
  - `base.ext`, puis `base_01.ext`, `base_02.ext`, etc. sans écrasement.
- Dossier Keep configurable au démarrage (`-keepDir`) et modifiable depuis l’UI.
- Toasts non bloquants pour feedback utilisateur.

## Installation

### Prérequis
- Java 21+
- Maven 3.9+

### Lancer

```bash
mvn clean package
java -jar target/myImageFilter-merge-dependencies.jar serve -port 8080 -keepDir /chemin/vers/KEEP
```

Puis ouvrir : `http://localhost:8080`

## Raccourcis clavier

### Mosaïque
- `←/→` : déplacer la sélection
- `Entrée` : ouvrir image en plein écran **ou** entrer dans dossier
- `↑` : monter au dossier parent
- `D` : supprimer image sélectionnée
- `K` : garder image sélectionnée
- clic image : plein écran
- clic dossier : entrer dans dossier

### Plein écran
- `←/→` : image précédente / suivante
- `D` : supprimer image courante puis passer automatiquement à la suivante/précédente
- `K` : garder image courante
- `Échap` : retour mosaïque dossier courant
- `↑` : quitter le plein écran et revenir en mosaïque du dossier courant
- `Retour arrière` : remonter au dossier parent (depuis mosaïque ou plein écran)

## API interne

- `GET /api/folder/entries?path=...`
- `GET /api/image?path=...`
- `GET /api/thumbnail?path=...&size=...`
- `POST /api/delete` body `{ "path": "..." }`
- `POST /api/keep` body `{ "path": "...", "keepDir": "..." }`
- `GET /api/config`
- `POST /api/config` body `{ "keepDir": "..." }`

## Notes de sécurité

- Les chemins sont normalisés en absolu côté backend.
- Les actions `delete`/`keep` sont refusées pour les dossiers.

## Tests

```bash
mvn test
```
