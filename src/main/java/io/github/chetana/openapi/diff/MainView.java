package io.github.chetana.openapi.diff;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Pre;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;

@Route("")
public class MainView extends VerticalLayout {

    private final OpenApiDiffService diffService;

    private final TextArea pmContractArea = new TextArea("Contrat OpenAPI de Référence (Design-First)");
    private final TextArea generatedContractArea = new TextArea("Contrat OpenAPI Généré (URL ou JSON/YAML brut)");
    private final Button compareButton = new Button("Comparer les contrats");
    
    private final VerticalLayout resultsLayout = new VerticalLayout();
    private final Pre consoleOutput = new Pre();
    private final Pre metadataOutput = new Pre();
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

        Details consoleDetails = new Details("Rapport de Structure (Console)", consoleOutput);
        consoleDetails.setOpened(true);
        consoleDetails.setWidthFull();

        Details metadataDetails = new Details("Changements de Métadonnées (Summary/Description)", metadataOutput);
        metadataDetails.setOpened(true);
        metadataDetails.setWidthFull();

        resultsLayout.add(statusLabel, consoleDetails, metadataDetails);

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

        consoleOutput.setText(result.consoleReport());
        metadataOutput.setText(result.metadataReport());
        
        // Scroll to results
        resultsLayout.getElement().executeJs("this.scrollIntoView({behavior: 'smooth'})");
    }
}
