package com.compomics.util.experiment.identification;

import com.compomics.util.experiment.personalization.ExperimentObject;
import java.util.HashMap;

/**
 * This class contains the index of a FASTA file.
 *
 * @author Marc Vaudel
 */
public class FastaIndex extends ExperimentObject {

    /**
     * The indexes of the inspected FASTA file.
     */
    private HashMap<String, Long> indexes;
    /**
     * The FASTA file name.
     */
    private String fileName;
    /**
     * The last time the indexed file was modified.
     */
    private Long lastModified;
    /**
     * Boolean indicating whether the database contains decoy sequences.
     */
    private boolean isDecoy;
    /**
     * Number of target sequences found in the database.
     */
    private int nTarget;

    /**
     * Constructor.
     *
     * @param indexes The indexes of the inspected FASTA file
     * @param fileName The FASTA file name
     * @param isDecoy If the FASTA file contains decoys or nor
     * @param nTarget Number of target sequences found in the database
     * @param lastModified a long indicating the last time the indexed file was
     * modified
     */
    public FastaIndex(HashMap<String, Long> indexes, String fileName, boolean isDecoy, int nTarget, long lastModified) {
        this.indexes = indexes;
        this.fileName = fileName;
        this.isDecoy = isDecoy;
        this.nTarget = nTarget;
        this.lastModified = lastModified;
    }

    /**
     * Returns a map of all indexes of the FASTA file (accession -> index).
     *
     * @return a map of all indexes of the FASTA file (accession -> index)
     */
    public HashMap<String, Long> getIndexes() {
        return indexes;
    }

    /**
     * Returns the index of the accession of interest.
     *
     * @param accession the accession of interest
     * @return the index of the accession of interest
     */
    public Long getIndex(String accession) {
        return indexes.get(accession);
    }

    /**
     * Returns the file name of the inspected FASTA file.
     *
     * @return the file name of the inspected FASTA file
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * Returns a boolean indicating whether the database contains decoy
     * sequences.
     *
     * @return a boolean indicating whether the database contains decoy
     * sequences
     */
    public boolean isDecoy() {
        return isDecoy;
    }

    /**
     * Returns the number of target sequences in the database.
     *
     * @return the number of target sequences in the database
     */
    public int getNTarget() {
        return nTarget;
    }

    /**
     * Returns when the file was last modified. Null if not set or for utilities
     * versions older than 3.11.30.
     *
     * @return a long indicating when the file was last modified
     */
    public Long getLastModified() {
        return lastModified;
    }
}