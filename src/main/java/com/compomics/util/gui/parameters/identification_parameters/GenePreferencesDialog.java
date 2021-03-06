package com.compomics.util.gui.parameters.identification_parameters;

import com.compomics.util.experiment.biology.taxonomy.SpeciesFactory;
import com.compomics.util.experiment.identification.identification_parameters.SearchParameters;
import com.compomics.util.experiment.identification.protein_sequences.FastaIndex;
import com.compomics.util.experiment.identification.protein_sequences.SequenceFactory;
import com.compomics.util.preferences.GenePreferences;
import java.io.File;
import java.util.HashMap;
import java.util.Vector;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.SwingConstants;

/**
 * Dialog for editing the Gene Mapping Preferences.
 *
 * @author Harald Barsnes
 */
public class GenePreferencesDialog extends javax.swing.JDialog {

    /**
     * True of the dialog was canceled by the user.
     */
    private boolean canceled = false;
    /**
     * Boolean indicating whether the settings can be edited by the user.
     */
    private boolean editable;
    /**
     * The gene preferences.
     */
    private GenePreferences genePreferences;
    /**
     * The search parameters.
     */
    private SearchParameters searchParameters;
    /**
     * The species factory.
     */
    private SpeciesFactory speciesFactory = SpeciesFactory.getInstance();
    /**
     * A map from the species names used in the drop down menu to the taxon.
     */
    private HashMap<String, Integer> speciesMap;

    /**
     * Creates a new GenePreferencesDialog with a frame as owner.
     *
     * @param parent the parent frame
     * @param genePreferences the gene preferences
     * @param searchParameters the search parameters
     * @param editable boolean indicating whether the settings can be edited by
     * the user
     */
    public GenePreferencesDialog(JFrame parent, GenePreferences genePreferences, SearchParameters searchParameters, boolean editable) {
        super(parent, true);
        this.editable = editable;
        this.genePreferences = genePreferences;
        this.searchParameters = searchParameters;
        initComponents();
        setUpGui();
        setLocationRelativeTo(parent);
        setVisible(true);
    }

    /**
     * Creates a new GenePreferencesDialog with a dialog as owner.
     *
     * @param owner the dialog owner
     * @param genePreferences the gene preferences
     * @param searchParameters the search parameters
     * @param editable boolean indicating whether the settings can be edited by
     * the user
     */
    public GenePreferencesDialog(JDialog owner, GenePreferences genePreferences, SearchParameters searchParameters, boolean editable) {
        super(owner, true);
        this.editable = editable;
        this.genePreferences = genePreferences;
        this.searchParameters = searchParameters;
        initComponents();
        setUpGui();
        setLocationRelativeTo(owner);
        setVisible(true);
    }

    /**
     * Sets up the GUI.
     */
    private void setUpGui() {
        speciesCmb.setRenderer(new com.compomics.util.gui.renderers.AlignedListCellRenderer(SwingConstants.CENTER));
        useMappingCmb.setRenderer(new com.compomics.util.gui.renderers.AlignedListCellRenderer(SwingConstants.CENTER));
        autoUpdateCmb.setRenderer(new com.compomics.util.gui.renderers.AlignedListCellRenderer(SwingConstants.CENTER));
        useMappingCmb.setEnabled(editable);
        autoUpdateCmb.setEnabled(editable);

        // set the species
        Vector availableSpecies = new Vector();
        speciesMap = new HashMap<String, Integer>();

        File fastaFile = searchParameters.getFastaFile();

        if (fastaFile != null) {

            int selectedIndex = 0;

            try {
                FastaIndex fastaIndex = SequenceFactory.getFastaIndex(fastaFile, false, null);
                HashMap<String, Integer> speciesOccurrence = fastaIndex.getSpecies();

                // Select the background species based on occurrence in the factory
                for (String uniprotTaxonomy : speciesOccurrence.keySet()) {

                    if (!uniprotTaxonomy.equals(SpeciesFactory.UNKNOWN)) {
                        Integer occurrence = speciesOccurrence.get(uniprotTaxonomy);

                        if (occurrence != null) {
                            try {
                                Integer taxon = speciesFactory.getUniprotTaxonomy().getId(uniprotTaxonomy, true);
                                if (taxon != null) {
                                    if (genePreferences.getSelectedBackgroundSpecies() != null
                                            && genePreferences.getSelectedBackgroundSpecies().intValue() == taxon) {
                                        selectedIndex = availableSpecies.size();
                                    }
                                    String tempSpecies = speciesFactory.getName(taxon) + " (" + occurrence + ")";
                                    availableSpecies.add(tempSpecies);
                                    speciesMap.put(tempSpecies, taxon);
                                }
                            } catch (Exception e) {
                                // taxon not available, ignore
                                e.printStackTrace();
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Not able to read the species, ignore
                e.printStackTrace();
            }

            speciesCmb.setModel(new DefaultComboBoxModel(availableSpecies));
            if (!availableSpecies.isEmpty()) {
                speciesCmb.setSelectedIndex(selectedIndex);
            }
        } else {
            availableSpecies.add("(no species available)");
            speciesCmb.setModel(new DefaultComboBoxModel(availableSpecies));
            speciesCmb.setEnabled(false);
        }

        // set if the gene mappings are to be used
        if (genePreferences.getUseGeneMapping()) {
            useMappingCmb.setSelectedIndex(0);
        } else {
            useMappingCmb.setSelectedIndex(1);
        }

        // set if the gene mappings are to be auto updated
        if (genePreferences.getAutoUpdate()) {
            autoUpdateCmb.setSelectedIndex(0);
        } else {
            autoUpdateCmb.setSelectedIndex(1);
        }
    }

    /**
     * Returns the gene preferences.
     *
     * @return the gene preferences
     */
    public GenePreferences getGenePreferences() {

        GenePreferences tempGenePreferences = new GenePreferences();

        tempGenePreferences.setUseGeneMapping(useMappingCmb.getSelectedIndex() == 0);
        tempGenePreferences.setAutoUpdate(autoUpdateCmb.getSelectedIndex() == 0);

        if (speciesCmb.isEnabled()) {
            tempGenePreferences.setSelectedBackgroundSpecies(speciesMap.get((String) speciesCmb.getSelectedItem()));
        }

        return tempGenePreferences;
    }

    /**
     * Indicates whether the user canceled the editing.
     *
     * @return a boolean indicating whether the user canceled the editing
     */
    public boolean isCanceled() {
        return canceled;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        backgroundPanel = new javax.swing.JPanel();
        mappingPanel = new javax.swing.JPanel();
        speciesLabel = new javax.swing.JLabel();
        speciesCmb = new javax.swing.JComboBox();
        useMappingLabel = new javax.swing.JLabel();
        useMappingCmb = new javax.swing.JComboBox();
        autoUpdateLabel = new javax.swing.JLabel();
        autoUpdateCmb = new javax.swing.JComboBox();
        okButton = new javax.swing.JButton();
        cancelButton = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setTitle("Gene Mappings");
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });

        backgroundPanel.setBackground(new java.awt.Color(230, 230, 230));

        mappingPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Settings"));
        mappingPanel.setOpaque(false);

        speciesLabel.setText("Species");

        useMappingLabel.setText("Use Mapping");

        useMappingCmb.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Yes", "No" }));

        autoUpdateLabel.setText("Auto Update");

        autoUpdateCmb.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Yes", "No" }));

        javax.swing.GroupLayout mappingPanelLayout = new javax.swing.GroupLayout(mappingPanel);
        mappingPanel.setLayout(mappingPanelLayout);
        mappingPanelLayout.setHorizontalGroup(
            mappingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(mappingPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(mappingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(speciesLabel)
                    .addComponent(useMappingLabel)
                    .addComponent(autoUpdateLabel))
                .addGap(18, 18, 18)
                .addGroup(mappingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(speciesCmb, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(useMappingCmb, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(autoUpdateCmb, javax.swing.GroupLayout.Alignment.TRAILING, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        mappingPanelLayout.setVerticalGroup(
            mappingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(mappingPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(mappingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(speciesLabel)
                    .addComponent(speciesCmb, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(mappingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(useMappingLabel)
                    .addComponent(useMappingCmb, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(mappingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(autoUpdateLabel)
                    .addComponent(autoUpdateCmb, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        okButton.setText("OK");
        okButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                okButtonActionPerformed(evt);
            }
        });

        cancelButton.setText("Cancel");
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout backgroundPanelLayout = new javax.swing.GroupLayout(backgroundPanel);
        backgroundPanel.setLayout(backgroundPanelLayout);
        backgroundPanelLayout.setHorizontalGroup(
            backgroundPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, backgroundPanelLayout.createSequentialGroup()
                .addGroup(backgroundPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(backgroundPanelLayout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(mappingPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(backgroundPanelLayout.createSequentialGroup()
                        .addGap(90, 361, Short.MAX_VALUE)
                        .addComponent(okButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cancelButton)))
                .addContainerGap())
        );

        backgroundPanelLayout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {cancelButton, okButton});

        backgroundPanelLayout.setVerticalGroup(
            backgroundPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(backgroundPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(mappingPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(backgroundPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(okButton)
                    .addComponent(cancelButton))
                .addContainerGap())
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(backgroundPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(backgroundPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    /**
     * Close the dialog.
     *
     * @param evt
     */
    private void okButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_okButtonActionPerformed
        dispose();
        setVisible(false);
    }//GEN-LAST:event_okButtonActionPerformed

    /**
     * Close the dialog.
     *
     * @param evt
     */
    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        canceled = true;
        okButtonActionPerformed(null);
    }//GEN-LAST:event_cancelButtonActionPerformed

    /**
     * Cancel the dialog.
     *
     * @param evt
     */
    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        cancelButtonActionPerformed(null);
    }//GEN-LAST:event_formWindowClosing

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JComboBox autoUpdateCmb;
    private javax.swing.JLabel autoUpdateLabel;
    private javax.swing.JPanel backgroundPanel;
    private javax.swing.JButton cancelButton;
    private javax.swing.JPanel mappingPanel;
    private javax.swing.JButton okButton;
    private javax.swing.JComboBox speciesCmb;
    private javax.swing.JLabel speciesLabel;
    private javax.swing.JComboBox useMappingCmb;
    private javax.swing.JLabel useMappingLabel;
    // End of variables declaration//GEN-END:variables
}
