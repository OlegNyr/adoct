package ru.gitverse.adoct.plugins.idea.mcp;

import com.intellij.openapi.options.Configurable;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;
import ru.gitverse.adoct.plugins.idea.mcp.McpSettingsService.TemplateState;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Страница настроек «Типы задач» (Settings → Tools → AsciiDocTools → Типы задач): master-detail —
 * слева список типов задач (Story / Bug / Task…), справa на каждый тип многострочный шаблон и
 * диаграмма состояний (PlantUML). И шаблон, и состояния привязаны к типу задачи. Сохраняет только
 * срез шаблонов, не затрагивая остальные настройки MCP (через {@link McpSettingsService#snapshot()}).
 */
public final class McpIssueTypesConfigurable implements Configurable {

    private DefaultTableModel typesModel;
    private JBTable typesTable;
    private JBTextArea templateBodyArea;
    private JBTextArea typeWorkflowArea;
    private final List<TemplateState> templatesData = new ArrayList<>();
    private int selectedType = -1;
    private JComponent component;

    @Override
    public @Nls String getDisplayName() {
        return "Типы задач";
    }

    @Override
    public @Nullable JComponent createComponent() {
        typesModel = new DefaultTableModel(new Object[0][], new String[] {"Тип задачи"}) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return true;
            }
        };
        typesTable = new JBTable(typesModel);
        typesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        typesTable.setTableHeader(null);
        templateBodyArea = new JBTextArea();
        typeWorkflowArea = new JBTextArea();

        typesTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            flushDetail();
            selectedType = typesTable.getSelectedRow();
            loadDetail(selectedType);
        });

        JComponent master = ToolbarDecorator.createDecorator(typesTable)
                .setAddAction(b -> {
                    typesModel.addRow(new Object[] {""});
                    templatesData.add(new TemplateState());
                    int row = typesModel.getRowCount() - 1;
                    typesTable.getSelectionModel().setSelectionInterval(row, row);
                    typesTable.editCellAt(row, 0);
                })
                .setRemoveAction(b -> {
                    int row = typesTable.getSelectedRow();
                    if (row >= 0) {
                        templatesData.remove(row);
                        typesModel.removeRow(row);
                        selectedType = -1;
                        loadDetail(-1);
                    }
                })
                .createPanel();

        JPanel detail = new JPanel(new GridLayout(2, 1, 0, 8));
        detail.add(titled("Шаблон задачи (многострочный):", new JBScrollPane(templateBodyArea)));
        detail.add(titled("Состояния задачи (PlantUML state):", new JBScrollPane(typeWorkflowArea)));

        JBSplitter split = new JBSplitter(false, "adoct.mcp.types.split", 0.3f);
        split.setFirstComponent(master);
        split.setSecondComponent(detail);

        JPanel root = new JPanel(new BorderLayout(0, 6));
        root.add(new JBLabel("На каждый тип задачи — шаблон оформления и диаграмма состояний (PlantUML)."),
                BorderLayout.NORTH);
        root.add(split, BorderLayout.CENTER);
        component = root;
        reset();
        return component;
    }

    @Override
    public boolean isModified() {
        return !sameTemplates(templatesFromUi(), McpSettingsService.getInstance().getTemplates());
    }

    @Override
    public void apply() {
        McpSettingsService.StateData state = McpSettingsService.getInstance().snapshot();
        state.templates = templatesFromUi();
        McpSettingsService.getInstance().loadState(state);
        McpServerService.getInstance().restart();
    }

    @Override
    public void reset() {
        typesModel.setRowCount(0);
        templatesData.clear();
        for (TemplateState t : McpSettingsService.getInstance().getTemplates()) {
            typesModel.addRow(new Object[] {t.issueType});
            templatesData.add(new TemplateState(t.issueType, t.body, t.workflow));
        }
        selectedType = -1;
        loadDetail(-1);
    }

    @Override
    public void disposeUIResources() {
        component = null;
        typesTable = null;
        typesModel = null;
        templateBodyArea = null;
        typeWorkflowArea = null;
        templatesData.clear();
        selectedType = -1;
    }

    // ---- master-detail ----

    private void flushDetail() {
        if (selectedType >= 0 && selectedType < templatesData.size()) {
            TemplateState t = templatesData.get(selectedType);
            t.body = templateBodyArea.getText();
            t.workflow = typeWorkflowArea.getText();
        }
    }

    private void loadDetail(int row) {
        boolean has = row >= 0 && row < templatesData.size();
        TemplateState t = has ? templatesData.get(row) : null;
        templateBodyArea.setText(t == null ? "" : t.body);
        typeWorkflowArea.setText(t == null ? "" : t.workflow);
        templateBodyArea.setEnabled(has);
        typeWorkflowArea.setEnabled(has);
    }

    private List<TemplateState> templatesFromUi() {
        if (typesTable.isEditing() && typesTable.getCellEditor() != null) {
            typesTable.getCellEditor().stopCellEditing();
        }
        flushDetail();
        List<TemplateState> out = new ArrayList<>();
        for (int r = 0; r < typesModel.getRowCount(); r++) {
            String issueType = Objects.toString(typesModel.getValueAt(r, 0), "").trim();
            if (issueType.isBlank()) {
                continue;
            }
            TemplateState d = templatesData.get(r);
            out.add(new TemplateState(issueType, d.body, d.workflow));
        }
        return out;
    }

    private static JComponent titled(String title, JComponent body) {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBorder(IdeBorderFactory.createTitledBorder(title));
        panel.add(body, BorderLayout.CENTER);
        return panel;
    }

    private static boolean sameTemplates(List<TemplateState> a, List<TemplateState> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!Objects.equals(a.get(i).issueType, b.get(i).issueType)
                    || !Objects.equals(a.get(i).body, b.get(i).body)
                    || !Objects.equals(a.get(i).workflow, b.get(i).workflow)) {
                return false;
            }
        }
        return true;
    }
}
