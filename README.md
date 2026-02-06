# OpenAPI Contract Diff 🚀

**OpenAPI Contract Diff** est une application web (Spring Boot + Vaadin) conçue pour garantir la cohérence entre vos designs d'API (Design-First) et l'implémentation réelle. Elle permet de comparer un contrat de référence avec un contrat généré (URL ou JSON/YAML) en mettant l'accent sur la lisibilité et l'exhaustivité.

## ✨ Fonctionnalités Clés

### 🔍 Comparaison Intelligente
- **Smart Matching** : Appariement des endpoints via `operationId` (permet de tolérer les changements d'URLs).
- **Filtrage par Scope** : L'outil ignore les endpoints présents dans le généré mais non définis dans votre contrat de référence.
- **Normalisation Textuelle** : Ignore les différences d'espaces blancs et d'indentation pour éviter les fausses alertes.

### 📊 Reporting Visuel (Interface Vaadin)
- **Changements de Métadonnées (Prioritaire)** : Comparaison côte à côte (**📜 Design First** vs **⚙️ Généré**) des summaries et descriptions.
- **Exploration Récursive** : Traque les changements de descriptions jusque dans les propriétés imbriquées des schémas et les items des listes.
- **Rapport de Structure** : Liste visuelle des changements techniques (Endpoints, Paramètres, Réponses) avec icônes et indicateurs de **Breaking Changes** (en rouge).
- **Détection d'ID Manquants** : Message d'erreur explicite si un `operationId` attendu n'est pas trouvé.

## 🛠 Installation & Lancement

### Local (Développement)
1. **Prérequis** : Java 17+ et Maven.
2. **Lancer l'app** :
   ```bash
   mvn spring-boot:run
   ```
3. **Accès** : L'interface est disponible sur `http://localhost:8080`.

### Production (Docker)
1. **Build l'image** :
   ```bash
   docker build -t openapi-contract-diff .
   ```
2. **Lancer le container** :
   ```bash
   docker run -p 8080:8080 openapi-contract-diff
   ```

## 💡 Utilisation
1. Collez votre contrat **Design-First** dans le champ de gauche.
2. Collez le JSON/YAML **Généré** (ou son URL `api-docs`) dans le champ de droite.
3. Cliquez sur **Comparer** pour obtenir un rapport instantané et structuré.
