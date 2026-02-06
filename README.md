# OpenAPI Contract Diff

Outil Java utilitaire permettant de comparer un contrat OpenAPI écrit (Design-First) avec le swagger généré dynamiquement par une application.

## Fonctionnalités Clés

- **Smart Mapping** : L'outil apparie les endpoints via l' `operationId` en priorité (permettant les changements d'URLs sans fausse alerte), puis par Path/Method si nécessaire.
- **Filtrage par Scope Contract** : Seuls les endpoints présents dans le fichier de référence sont comparés (ignore le reste du swagger généré).
- **Normalisation des Textes** : 
    - Ignore les différences d'espaces blancs, indentations et retours à la ligne.
    - Conserve les balises HTML (ex: `<br>`) et respecte la casse.
- **Reporting Manuel des Métadonnées** : Inclusion forcée des différences de `summary` et `description` (Opérations, Paramètres, Schémas récursifs) car la librairie de base les masque parfois.
- **Formatage Markdown Optimisé** : Rapport généré avec une structure hiérarchique lisible directement dans l'aperçu de votre IDE.

## Prérequis

- **Java 17+**
- **Maven**
- L'application cible doit être lancée si vous utilisez une URL pour la comparaison.

## Installation

```bash
mvn clean package
```

Le fichier JAR autonome est généré dans `target/openapi-contract-diff-1.0-SNAPSHOT.jar`.

## Utilisation

```bash
java -jar target/openapi-contract-diff-1.0-SNAPSHOT.jar \
  <chemin_contrat_reference.yml> \
  "<url_ou_chemin_genere>" \
  [rapport_markdown]
```

### Exemple :
```bash
java -jar target/openapi-contract-diff-1.0-SNAPSHOT.jar \
  ./specs/api-contract.yml \
  "http://localhost:8080/v3/api-docs" \
  rapport-diff
```
*Note : Si l'extension `.md` est omise pour le rapport, elle sera ajoutée automatiquement.*

## Analyse du Rapport

1. **Console** : Affiche le log de changement structurel (Breaking vs Non-Breaking) et un résumé des changements de métadonnées.
2. **Markdown** : Fournit une vue détaillée avec comparaison côte à côte (Référence vs Généré) pour les textes, facilitant la validation visuelle.
