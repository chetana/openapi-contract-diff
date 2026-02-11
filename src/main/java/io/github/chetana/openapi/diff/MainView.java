package io.github.chetana.openapi.diff;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Pre;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.router.Route;

@Route("")
public class MainView extends VerticalLayout {

    private final OpenApiDiffService diffService;

    private final TextArea pmContractArea = new TextArea("Contrat OpenAPI de Référence (Design-First)");
    private final TextArea generatedContractArea = new TextArea("Contrat OpenAPI Généré (URL ou JSON/YAML brut)");
    private final Button compareButton = new Button("Comparer les contrats");
    private final Button exportCsvButton = new Button("Exporter en CSV");
    private final Anchor exportAnchor = new Anchor();
    
    private final VerticalLayout resultsLayout = new VerticalLayout();
    private final VerticalLayout structureChangesLayout = new VerticalLayout();
    private final VerticalLayout metadataChangesLayout = new VerticalLayout();
    private final Span statusLabel = new Span();
    
    public MainView(OpenApiDiffService diffService) {
        this.diffService = diffService;

        setupLayout();
        setupClickListeners();
    }

    private void setupLayout() {
        setPadding(true);
        setSpacing(true);
        setAlignItems(Alignment.STRETCH);

        H1 title = new H1("OpenAPI Contract Diff");
        title.getStyle().set("margin-top", "0");

        pmContractArea.setPlaceholder("Collez le YAML du contrat de référence ici...");
        pmContractArea.setHeight("300px");
        pmContractArea.setWidthFull();

        generatedContractArea.setPlaceholder("Collez le JSON/YAML généré ici, ou l'URL (ex: https://api.prod.com/v3/api-docs)");
        generatedContractArea.setHeight("300px");
        generatedContractArea.setWidthFull();

        compareButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        compareButton.setWidthFull();
        
        HorizontalLayout inputsLayout = new HorizontalLayout(pmContractArea, generatedContractArea);
        inputsLayout.setWidthFull();
        inputsLayout.setFlexGrow(1, pmContractArea);
        inputsLayout.setFlexGrow(1, generatedContractArea);

        resultsLayout.setVisible(false);
        resultsLayout.setPadding(false);
        
        statusLabel.getStyle().set("font-weight", "bold");
        statusLabel.getStyle().set("font-size", "1.2em");
        statusLabel.getStyle().set("flex-grow", "1");

        exportCsvButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        exportAnchor.add(exportCsvButton);
        exportAnchor.getElement().setAttribute("download", true);
        exportAnchor.setVisible(false);

        HorizontalLayout headerLayout = new HorizontalLayout(statusLabel, exportAnchor);
        headerLayout.setWidthFull();
        headerLayout.setAlignItems(Alignment.CENTER);

        Details metadataDetails = new Details("Changements de Métadonnées (Summary/Description)", metadataChangesLayout);
        metadataDetails.setOpened(true);
        metadataDetails.setWidthFull();

        Details structureDetails = new Details("Rapport de Structure (Changements techniques)", structureChangesLayout);
        structureDetails.setOpened(true);
        structureDetails.setWidthFull();

        resultsLayout.add(headerLayout, metadataDetails, structureDetails);

        add(title, inputsLayout, compareButton, resultsLayout);
    }

    private void setupClickListeners() {
        compareButton.addClickListener(event -> {
            String pmContent = pmContractArea.getValue();
            String genInput = generatedContractArea.getValue();

            if (pmContent == null || pmContent.isBlank() || genInput == null || genInput.isBlank()) {
                Notification.show("Veuillez remplir les deux champs (Contrat de référence et Contrat généré).", 3000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            try {
                OpenApiDiffService.DiffResult result = diffService.compare(pmContent, genInput);
                displayResults(result);
            } catch (Exception e) {
                Notification.show("Erreur lors de la comparaison : " + e.getMessage(), 5000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
    }

    private void displayResults(OpenApiDiffService.DiffResult result) {
        resultsLayout.setVisible(true);
        metadataChangesLayout.removeAll();
        structureChangesLayout.removeAll();
        
        if (result.isDifferent() || !result.missingOperationIds().isEmpty()) {
            exportAnchor.setVisible(true);
            StreamResource resource = new StreamResource("openapi-diff.csv", 
                () -> diffService.exportToCsv(result));
            exportAnchor.setHref(resource);
        } else {
            exportAnchor.setVisible(false);
        }

        if (!result.missingOperationIds().isEmpty()) {
            String missing = String.join(", ", result.missingOperationIds());
            statusLabel.setText("❌ Le contrat avec l'operation ID " + missing + " n'a pas été trouvé");
            statusLabel.getStyle().set("color", "var(--lumo-error-color)");
        } else if (result.isDifferent()) {
            statusLabel.setText("❌ Des différences ont été détectées.");
            statusLabel.getStyle().set("color", "var(--lumo-error-color)");
        } else {
            statusLabel.setText("✅ Match! Les contrats sont conformes.");
            statusLabel.getStyle().set("color", "var(--lumo-success-color)");
        }

        // Structure Changes
        if (result.structureChanges().isEmpty()) {
            structureChangesLayout.add(new Span("Aucun changement de structure détecté."));
        } else {
            result.structureChanges().forEach(change -> {
                HorizontalLayout row = new HorizontalLayout();
                row.setWidthFull();
                row.setAlignItems(Alignment.CENTER);
                row.getStyle().set("border-bottom", "1px solid var(--lumo-contrast-10pct)");
                row.setPadding(true);

                String icon = switch (change.changeType()) {
                    case "NEW" -> "➕";
                    case "REMOVED" -> "❌";
                    default -> change.isBreaking() ? "⚠️" : "🔄";
                };

                Span method = new Span(change.method());
                method.getStyle().set("font-weight", "bold");
                method.getStyle().set("min-width", "60px");
                
                Span path = new Span(change.path());
                path.getStyle().set("flex-grow", "1");

                VerticalLayout details = new VerticalLayout();
                details.setSpacing(false);
                details.setPadding(false);
                change.details().forEach(d -> {
                    Span s = new Span("• " + d);
                    s.getStyle().set("font-size", "0.9em");
                    if (change.isBreaking()) s.getStyle().set("color", "var(--lumo-error-color)");
                    details.add(s);
                });

                row.add(new Span(icon), method, path, details);
                structureChangesLayout.add(row);
            });
        }
        
        // Metadata Changes
        result.metadataChanges().forEach(change -> {
            VerticalLayout changeCard = new VerticalLayout();
            changeCard.setSpacing(false);
            changeCard.setPadding(true);
            changeCard.getStyle().set("border", "1px solid var(--lumo-contrast-10pct)");
            changeCard.getStyle().set("border-radius", "var(--lumo-border-radius-m)");
            changeCard.setWidthFull();

            Span header = new Span(change.method() + " " + change.path() + " (" + change.field() + ")");
            header.getStyle().set("font-weight", "bold");
            header.getStyle().set("color", "var(--lumo-primary-color)");

            HorizontalLayout comparison = new HorizontalLayout();
            comparison.setWidthFull();
            comparison.setSpacing(true);

            VerticalLayout left = new VerticalLayout(new Span("📜 Contract Design First"));
            Pre preLeft = new Pre(change.designFirstValue());
            preLeft.getStyle().set("white-space", "pre-wrap");
            preLeft.getStyle().set("word-break", "break-word");
            preLeft.setWidthFull();
            left.add(preLeft);
            left.setPadding(false);
            left.setSpacing(false);
            left.getStyle().set("background-color", "var(--lumo-contrast-5pct)");
            left.getStyle().set("padding", "10px");
            left.getStyle().set("border-radius", "4px");
            left.setWidth("50%");

            VerticalLayout right = new VerticalLayout(new Span("⚙️ Contrat généré"));
            Pre preRight = new Pre(change.generatedValue());
            preRight.getStyle().set("white-space", "pre-wrap");
            preRight.getStyle().set("word-break", "break-word");
            preRight.setWidthFull();
            right.add(preRight);
            right.setPadding(false);
            right.setSpacing(false);
            right.getStyle().set("background-color", "var(--lumo-contrast-5pct)");
            right.getStyle().set("padding", "10px");
            right.getStyle().set("border-radius", "4px");
            right.setWidth("50%");

            comparison.add(left, right);
            changeCard.add(header, comparison);
            metadataChangesLayout.add(changeCard);
        });
        
        // Scroll to results
        resultsLayout.getElement().executeJs("this.scrollIntoView({behavior: 'smooth'})");
    }
}
