package com.compomics.util.experiment.identification.ptm.ptmscores;

import com.compomics.util.Util;
import com.compomics.util.experiment.biology.Ion;
import com.compomics.util.experiment.biology.IonFactory;
import com.compomics.util.experiment.biology.NeutralLoss;
import com.compomics.util.experiment.biology.PTM;
import com.compomics.util.experiment.biology.Peptide;
import com.compomics.util.experiment.identification.spectrum_annotation.NeutralLossesMap;
import com.compomics.util.experiment.identification.matches.IonMatch;
import com.compomics.util.experiment.identification.matches.ModificationMatch;
import com.compomics.util.experiment.identification.spectrum_annotation.spectrum_annotators.PeptideSpectrumAnnotator;
import com.compomics.util.experiment.massspectrometry.MSnSpectrum;
import com.compomics.util.experiment.massspectrometry.Peak;
import com.compomics.util.experiment.massspectrometry.Spectrum;
import com.compomics.util.math.statistics.distributions.BinomialDistribution;
import com.compomics.util.experiment.identification.spectrum_annotation.AnnotationSettings;
import com.compomics.util.preferences.SequenceMatchingPreferences;
import com.compomics.util.experiment.identification.spectrum_annotation.SpecificAnnotationSettings;
import com.compomics.util.maps.KeyUtils;
import com.compomics.util.math.BasicMathFunctions;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import org.apache.commons.math.MathException;
import org.apache.commons.math.util.FastMath;

/**
 * This class estimates the PhosphoRS score as described in
 * http://www.ncbi.nlm.nih.gov/pubmed/22073976. Warning: the calculation in its
 * present form is very slow for multiply modified peptides, peptides with many
 * modification sites, and noisy spectra. Typically, avoid scoring deamidation
 * sites.
 *
 * @author Marc Vaudel
 */
public class PhosphoRS {

    /**
     * The window size in m/z.
     */
    public static final double WINDOW_SIZE = 100.0;
    /**
     * The maximal depth to use per window. 8 in the original paper, Must be
     * greater than 1.
     */
    public static final int MAX_DEPTH = 8;
    /**
     * The minimal depth to use per window.
     */
    public static final int MIN_DEPTH = 2;
    /**
     * The number of binomial distributions kept in cache.
     */
    private static int distributionCacheSize = 1000;
    /**
     * The binomial distributions cache.
     */
    private static HashMap<Double, HashMap<Integer, BinomialDistribution>> distributionCache = new HashMap<Double, HashMap<Integer, BinomialDistribution>>();

    /**
     * Returns the PhosphoRS sequence probabilities for the PTM possible
     * locations. 1 is the first amino acid. The N-terminus is indexed 0 and the
     * C-terminus with the peptide length+1. Note that PTMs found on peptides
     * must be loaded in the PTM factory
     * (com.compomics.util.experiment.biology.PTMFactory), and if the scoring
     * involves protein terminal PTMs, the protein sequences must be loaded in
     * the sequence factory
     * (com.compomics.util.experiment.identification.SequenceFactory) and
     * indexed using the protein tree (see getDefaultProteinTree in
     * SequenceFactory). PTMs of same mass should be scored together and given
     * in the PTMs list. Neutral losses of mass equal to the mass of the PTM
     * will be ignored. Neutral losses to be accounted for should be given in
     * the SpecificAnnotationSettings and will be ignored if
     * accountNeutralLosses is false.
     *
     * @param peptide the peptide of interest
     * @param ptms the PTMs to score, for instance different phosphorylations
     * (the PTMs are considered as indistinguishable, i.e. of same mass)
     * @param spectrum the corresponding spectrum
     * @param annotationSettings the global annotation settings
     * @param specificAnnotationSettings the annotation settings specific to
     * this peptide and spectrum
     * @param accountNeutralLosses a boolean indicating whether or not the
     * calculation shall account for neutral losses.
     * @param sequenceMatchingPreferences the sequence matching preferences for
     * peptide to protein mapping
     * @param ptmSequenceMatchingPreferences the sequence matching preferences
     * for PTM to peptide mapping
     * @param spectrumAnnotator the peptide spectrum annotator to use for
     * spectrum annotation, can be null
     *
     * @return a map site &gt; phosphoRS site probability
     *
     * @throws java.io.IOException exception thrown whenever an error occurred
     * while reading or writing a file
     * @throws java.lang.InterruptedException exception thrown whenever a
     * threading issue occurred while scoring the PTM
     * @throws java.lang.ClassNotFoundException exception thrown whenever an
     * error occurred while deserializing an object from the protein tree (the
     * protein sequence index)
     * @throws java.sql.SQLException exception thrown whenever an error occurred
     * while interacting with the protein tree
     * @throws org.apache.commons.math.MathException exception thrown whenever a
     * math error occurred while computing the score.
     */
    public static HashMap<Integer, Double> getSequenceProbabilities(Peptide peptide, ArrayList<PTM> ptms, MSnSpectrum spectrum, AnnotationSettings annotationSettings,
            SpecificAnnotationSettings specificAnnotationSettings, boolean accountNeutralLosses, SequenceMatchingPreferences sequenceMatchingPreferences,
            SequenceMatchingPreferences ptmSequenceMatchingPreferences, PeptideSpectrumAnnotator spectrumAnnotator)
            throws IOException, InterruptedException, ClassNotFoundException, SQLException, MathException {

        if (ptms.isEmpty()) {
            throw new IllegalArgumentException("No PTM given for PhosphoRS calculation.");
        }

        if (spectrumAnnotator == null) {
            spectrumAnnotator = new PeptideSpectrumAnnotator();
        }

        int nPTM = 0;
        if (peptide.isModified()) {
            for (ModificationMatch modMatch : peptide.getModificationMatches()) {
                if (modMatch.isVariable()) {
                    for (PTM ptm : ptms) {
                        if (ptm.getName().equals(modMatch.getTheoreticPtm())) {
                            nPTM++;
                        }
                    }
                }
            }
        }
        if (nPTM == 0) {
            throw new IllegalArgumentException("Given PTMs not found in the peptide for PhosphoRS calculation.");
        }

        double ptmMass = ptms.get(0).getMass();

        NeutralLossesMap annotationNeutralLosses = specificAnnotationSettings.getNeutralLossesMap(),
                scoringLossesMap = new NeutralLossesMap();
        if (accountNeutralLosses) {
            // here annotation are sequence and modification independant
            for (String neutralLossName : annotationNeutralLosses.getAccountedNeutralLosses()) {
                NeutralLoss neutralLoss = NeutralLoss.getNeutralLoss(neutralLossName);
                if (Math.abs(neutralLoss.getMass() - ptmMass) > specificAnnotationSettings.getFragmentIonAccuracyInDa(spectrum.getMaxMz())) {
                    scoringLossesMap.addNeutralLoss(neutralLoss, 1, 1);
                }
            }
        }
        SpecificAnnotationSettings scoringAnnotationSetttings = specificAnnotationSettings.clone();
        scoringAnnotationSetttings.setNeutralLossesMap(scoringLossesMap);
        HashMap<Ion.IonType, HashSet<Integer>> ions = specificAnnotationSettings.getIonTypes(),
                newIons = new HashMap<Ion.IonType, HashSet<Integer>>(1);
        for (Ion.IonType ionType : ions.keySet()) {
            if (ionType == Ion.IonType.PEPTIDE_FRAGMENT_ION) {
                newIons.put(ionType, ions.get(ionType));
            }
        }
        scoringAnnotationSetttings.setSelectedIonsMap(newIons);
        ArrayList<Integer> possibleSites = new ArrayList<Integer>();

        int peptideLength = peptide.getSequence().length();

        for (PTM ptm : ptms) {
            if (ptm.isNTerm()) {
                if (peptide.getPotentialModificationSites(ptm, sequenceMatchingPreferences, ptmSequenceMatchingPreferences).contains(1)) {
                    possibleSites.add(0);
                }
            } else if (ptm.isCTerm()) {
                if (peptide.getPotentialModificationSites(ptm, sequenceMatchingPreferences, ptmSequenceMatchingPreferences).contains(peptideLength)) {
                    possibleSites.add(peptideLength + 1);
                }
            } else {
                for (int potentialSite : peptide.getPotentialModificationSites(ptm, sequenceMatchingPreferences, ptmSequenceMatchingPreferences)) {
                    if (!possibleSites.contains(potentialSite)) {
                        possibleSites.add(potentialSite);
                    }
                }
            }
        }
        Collections.sort(possibleSites);

        HashMap<String, Double> profileToScoreMap = new HashMap<String, Double>(possibleSites.size());
        HashMap<String, ArrayList<Integer>> profileToSitesMap = new HashMap<String, ArrayList<Integer>>(possibleSites.size());

        if (possibleSites.size() > nPTM) {

            spectrum = filterSpectrum(spectrum, scoringAnnotationSetttings);

            Peptide noModPeptide = Peptide.getNoModPeptide(peptide, ptms);
            ArrayList<ArrayList<Integer>> possibleProfiles = getPossibleModificationProfiles(possibleSites, nPTM);
            ArrayList<String> possibleProfileKeys = new ArrayList<String>(possibleProfiles.size());
            for (ArrayList<Integer> profile : possibleProfiles) {
                String profileKey = KeyUtils.getKey(profile);
                possibleProfileKeys.add(profileKey);
                profileToSitesMap.put(profileKey, profile);
            }

            HashMap<String, Peptide> profileToPeptide = getPossiblePeptidesMap(peptide, ptms, possibleProfiles);
            HashMap<String, HashMap<Integer, HashMap<Integer, ArrayList<Ion>>>> profileToPossibleFragments = getPossiblePeptideFragments(profileToPeptide, scoringAnnotationSetttings);
            HashMap<String, Integer> profileToN = getPossiblePeptideToN(profileToPeptide, profileToPossibleFragments, spectrumAnnotator, scoringAnnotationSetttings);

            HashMap<Double, ArrayList<String>> siteDeterminingIonsMap = getSiteDeterminingIons(noModPeptide, possibleProfiles, ptms, spectrumAnnotator, scoringAnnotationSetttings);
            ArrayList<Double> siteDeterminingIons = new ArrayList<Double>(siteDeterminingIonsMap.keySet());

            double minMz = spectrum.getMinMz(), maxMz = spectrum.getMaxMz(), tempMax;

            HashMap<Double, Peak> reducedSpectrum = new HashMap<Double, Peak>();

            double d = specificAnnotationSettings.getFragmentIonAccuracy();
            double dOverW = d / WINDOW_SIZE;
            dOverW = -FastMath.log10(dOverW);
            int nDecimals = ((int) dOverW) + 1;
            double halfWindow = WINDOW_SIZE / 2;

            while (minMz < maxMz) {

                tempMax = minMz + WINDOW_SIZE;

                if (specificAnnotationSettings.isFragmentIonPpm()) {
                    Double refMz = minMz + halfWindow;
                    d = specificAnnotationSettings.getFragmentIonAccuracyInDa(refMz);
                    dOverW = d / WINDOW_SIZE;
                    dOverW = -FastMath.log10(dOverW);
                    nDecimals = ((int) dOverW) + 1;
                }

                HashMap<Double, Peak> extractedPeakList = spectrum.getSubSpectrum(minMz, tempMax);

                if (!extractedPeakList.isEmpty()) {

                    MSnSpectrum tempSpectrum = new MSnSpectrum(spectrum.getLevel(), spectrum.getPrecursor(), spectrum.getSpectrumTitle()
                            + "_PhosphoRS_minMZ_" + minMz, extractedPeakList, spectrum.getFileName());

                    ArrayList<MSnSpectrum> spectra = getReducedSpectra(tempSpectrum);

                    HashMap<String, HashSet<Double>> profileToSiteDeterminingIonsMz = new HashMap<String, HashSet<Double>>(siteDeterminingIons.size());
                    for (double ionMz : siteDeterminingIons) {
                        if (ionMz > minMz && ionMz <= tempMax) {
                            ArrayList<String> profiles = siteDeterminingIonsMap.get(ionMz);
                            for (String profileKey : profiles) {
                                HashSet<Double> mzs = profileToSiteDeterminingIonsMz.get(profileKey);
                                if (mzs == null) {
                                    mzs = new HashSet<Double>(1);
                                    profileToSiteDeterminingIonsMz.put(profileKey, mzs);
                                }
                                mzs.add(ionMz);
                            }
                        }
                    }

                    if (!profileToSiteDeterminingIonsMz.isEmpty()) {

                        ArrayList<ArrayList<Double>> deltas = new ArrayList<ArrayList<Double>>(spectra.size());
                        int nDeltas = 0;

                        for (MSnSpectrum currentSpectrum : spectra) {
                            ArrayList<Double> bigPs = new ArrayList<Double>(possibleProfileKeys.size());
                            ArrayList<Double> currentDeltas = new ArrayList<Double>(possibleProfileKeys.size());
                            ArrayList<HashSet<Double>> scored = new ArrayList<HashSet<Double>>(possibleProfileKeys.size());
                            boolean profileWithNoSiteDeterminingIonsScored = false;
                            double currentP = getp(currentSpectrum, WINDOW_SIZE, d, nDecimals);
                            for (String profileKey : possibleProfileKeys) {
                                HashSet<Double> tempSiteDeterminingIons = profileToSiteDeterminingIonsMz.get(profileKey);
                                if (tempSiteDeterminingIons == null) {
                                    if (!profileWithNoSiteDeterminingIonsScored) {
                                        profileWithNoSiteDeterminingIonsScored = true;
                                        Peptide tempPeptide = profileToPeptide.get(profileKey);
                                        HashMap<Integer, HashMap<Integer, ArrayList<Ion>>> possibleFragmentIons = profileToPossibleFragments.get(profileKey);
                                        Integer n = profileToN.get(profileKey);
                                        Double bigP = getPhosphoRsScoreP(tempPeptide, possibleFragmentIons, currentSpectrum, currentP, n, spectrumAnnotator, annotationSettings, scoringAnnotationSetttings);
                                        BasicMathFunctions.checkProbabilityRange(bigP);
                                        bigPs.add(bigP);
                                    }
                                } else {
                                    boolean alreadyScored = false;
                                    for (HashSet<Double> scoredIons : scored) {
                                        if (Util.sameSets(tempSiteDeterminingIons, scoredIons)) {
                                            alreadyScored = true;
                                            break;
                                        }
                                    }
                                    if (!alreadyScored) {
                                        Peptide tempPeptide = profileToPeptide.get(profileKey);
                                        Integer n = profileToN.get(profileKey);
                                        HashMap<Integer, HashMap<Integer, ArrayList<Ion>>> possibleFragmentIons = profileToPossibleFragments.get(profileKey);
                                        Double bigP = getPhosphoRsScoreP(tempPeptide, possibleFragmentIons, currentSpectrum, currentP, n, spectrumAnnotator, annotationSettings, scoringAnnotationSetttings);
                                        BasicMathFunctions.checkProbabilityRange(bigP);
                                        bigPs.add(bigP);
                                        scored.add(tempSiteDeterminingIons);
                                    }
                                }
                            }
                            Collections.sort(bigPs);
                            for (int j = 0; j < bigPs.size() - 1; j++) {
                                Double pJ = bigPs.get(j);
                                Double pJPlusOne = bigPs.get(j + 1);
                                Double delta = pJ / pJPlusOne;
                                currentDeltas.add(delta);
                            }
                            if (currentDeltas.size() > nDeltas) {
                                nDeltas = currentDeltas.size();
                            }
                            deltas.add(currentDeltas);
                        }

                        int bestI = 0;
                        Double largestDelta = 0.0;

                        for (int j = 0; j < nDeltas && largestDelta == 0.0; j++) {
                            for (int i = 0; i < deltas.size(); i++) {
                                ArrayList<Double> tempDeltas = deltas.get(i);
                                if (j < tempDeltas.size() && tempDeltas.get(j) > largestDelta) {
                                    largestDelta = tempDeltas.get(j);
                                    bestI = i;
                                }
                            }
                        }

                        if (bestI < MIN_DEPTH - 1 && MIN_DEPTH -1 < spectra.size()) {
                            bestI = MIN_DEPTH - 1;
                        }
                        if (bestI > MAX_DEPTH - 1) {
                            bestI = MAX_DEPTH - 1;
                        }

                        reducedSpectrum.putAll(spectra.get(bestI).getPeakMap());

                    } else {

                        Double bestP = 0.0;
                        int bestI = 0;

                        HashMap<Integer, ArrayList<Ion>> expectedFragmentIons = spectrumAnnotator.getExpectedIons(scoringAnnotationSetttings, peptide);
                        int nExpectedFragmentIons = 0;
                        for (ArrayList<Ion> expectedIons : expectedFragmentIons.values()) {
                            nExpectedFragmentIons += expectedIons.size();
                        }
                        IonFactory fragmentFactory = IonFactory.getInstance();
                        HashMap<Integer, HashMap<Integer, ArrayList<Ion>>> possibleFragmentIons = fragmentFactory.getFragmentIons(peptide, scoringAnnotationSetttings);
                        for (int i = 0; i < spectra.size(); i++) {
                            MSnSpectrum currentSpectrum = spectra.get(i);
                            double currentP = getp(currentSpectrum, WINDOW_SIZE, d, nDecimals);
                            Double bigP = getPhosphoRsScoreP(peptide, possibleFragmentIons, currentSpectrum, currentP, nExpectedFragmentIons, spectrumAnnotator, annotationSettings, scoringAnnotationSetttings);
                            BasicMathFunctions.checkProbabilityRange(bigP);
                            if (bigP < bestP) {
                                bestP = bigP;
                                bestI = i;
                            }
                        }

                        reducedSpectrum.putAll(spectra.get(bestI).getPeakMap());
                    }
                }

                minMz = tempMax;
            }

            MSnSpectrum phosphoRsSpectrum = new MSnSpectrum(spectrum.getLevel(), spectrum.getPrecursor(), spectrum.getSpectrumTitle() + "_phosphoRS", reducedSpectrum, spectrum.getFileName());

            double w = spectrum.getMaxMz() - spectrum.getMinMz();
            if (specificAnnotationSettings.isFragmentIonPpm()) {
                Double refMz = spectrum.getMinMz() + (w / 2);
                d = specificAnnotationSettings.getFragmentIonAccuracyInDa(refMz);
            }
            dOverW = d / w;
            dOverW = -FastMath.log10(dOverW);
            nDecimals = ((int) dOverW) + 1;
            double currentP = getp(phosphoRsSpectrum, w, d, nDecimals);
            HashMap<String, Double> pInvMap = new HashMap<String, Double>(possibleProfileKeys.size());
            Double pInvTotal = 0.0;

            for (String profileKey : possibleProfileKeys) {
                Peptide tempPeptide = profileToPeptide.get(profileKey);
                Integer n = profileToN.get(profileKey);
                HashMap<Integer, HashMap<Integer, ArrayList<Ion>>> possibleFragmentIons = profileToPossibleFragments.get(profileKey);
                Double bigP = getPhosphoRsScoreP(tempPeptide, possibleFragmentIons, phosphoRsSpectrum, currentP, n, spectrumAnnotator, annotationSettings, scoringAnnotationSetttings);
                BasicMathFunctions.checkProbabilityRange(bigP);
                Double pInv = 1.0 / bigP;
                pInvMap.put(profileKey, pInv);
                pInvTotal += pInv;
            }
            if (pInvTotal <= 0) {
                throw new IllegalArgumentException("PhosphoRS probability <= 0.");
            }

            for (String profileKey : possibleProfileKeys) {
                Double phosphoRsProbability = pInvMap.get(profileKey) / pInvTotal; //in percent
                BasicMathFunctions.checkProbabilityRange(phosphoRsProbability);
                phosphoRsProbability *= 100;
                profileToScoreMap.put(profileKey, phosphoRsProbability);
            }

        } else if (possibleSites.size() == nPTM) {
            String profileKey = KeyUtils.getKey(possibleSites);
            profileToScoreMap.put(profileKey, 100.0);
            profileToSitesMap.put(profileKey, possibleSites);
        } else {
            throw new IllegalArgumentException("Found less potential modification sites than PTMs during PhosphoRS calculation. Peptide key: " + peptide.getKey());
        }

        HashMap<Integer, Double> scores = new HashMap<Integer, Double>();
        for (String profile : profileToScoreMap.keySet()) {
            Double score = profileToScoreMap.get(profile);
            ArrayList<Integer> sites = profileToSitesMap.get(profile);
            for (Integer site : sites) {
                Double previousScore = scores.get(site);
                if (previousScore == null) {
                    scores.put(site, score);
                } else {
                    Double newScore = score + previousScore;
                    scores.put(site, newScore);
                }
            }
        }

        for (int site : possibleSites) {
            if (!scores.keySet().contains(site)) {
                throw new IllegalArgumentException("Site " + site + " not scored for modification " + ptmMass + " in spectrum " + spectrum.getSpectrumTitle() + " of file " + spectrum.getFileName() + ".");
            }
        }

        return scores;
    }

    /**
     * Returns the PhosphoRS score of the given peptide on the given spectrum.
     * This method returns P and not -10.log(P).
     *
     * @param peptide the peptide of interest
     * @param spectrum the spectrum of interest
     * @param p the probability for a calculated fragment matching one of the
     * experimental masses by chance as estimated by PhosphoRS
     * @param n the number of expected ions
     * @param spectrumAnnotator spectrum annotator
     * @param annotationSettings the global annotation settings
     * @param scoringAnnotationSettings the annotation settings specific to this
     * peptide and spectrum
     *
     * @return the phosphoRS score
     */
    private static Double getPhosphoRsScoreP(Peptide peptide, HashMap<Integer, HashMap<Integer, ArrayList<Ion>>> possiblePeptideFragments, MSnSpectrum spectrum, double p, int n, PeptideSpectrumAnnotator spectrumAnnotator,
            AnnotationSettings annotationSettings, SpecificAnnotationSettings scoringAnnotationSettings) throws MathException {

        BinomialDistribution distribution = null;
        HashMap<Integer, BinomialDistribution> distributionsAtP = distributionCache.get(p);
        boolean inCache = true;
        if (distributionsAtP != null) {
            distribution = distributionsAtP.get(n);
        }
        if (distribution == null) {
            distribution = new BinomialDistribution(n, p);
            inCache = false;
        }

        ArrayList<IonMatch> matches = spectrumAnnotator.getSpectrumAnnotation(annotationSettings, scoringAnnotationSettings, spectrum, peptide, possiblePeptideFragments);
        int k = 0;
        for (IonMatch ionMatch : matches) {
            if (ionMatch.ion.getType() == Ion.IonType.PEPTIDE_FRAGMENT_ION) {
                k++;
            }
        }
        if (k == 0) {
            return 1.0;
        }

        Double result = distribution.getDescendingCumulativeProbabilityAt((double) k);
        if (!inCache && !distribution.isCacheEmpty()) {
            addDistributionToCache(p, n, distribution);
        }
        return result;
    }

    /**
     * Adds a distribution to the cache and manages the cache size.
     *
     * @param p the distribution p
     * @param n the distribution n
     */
    private static synchronized void addDistributionToCache(double p, int n, BinomialDistribution binomialDistribution) {
        if (distributionCache.size() >= distributionCacheSize) {
            HashSet<Double> keys = new HashSet<Double>(distributionCache.keySet());
            for (Double key : keys) {
                distributionCache.remove(key);
                if (distributionCache.size() < distributionCacheSize) {
                    break;
                }
            }
        }
        HashMap<Integer, BinomialDistribution> distributionsAtP = distributionCache.get(p);
        if (distributionsAtP == null) {
            distributionsAtP = new HashMap<Integer, BinomialDistribution>(2);
            distributionCache.put(p, distributionsAtP);
        }
        distributionsAtP.put(n, binomialDistribution);
    }

    /**
     * The probability p for a calculated fragment matching one of the
     * experimental masses by chance as estimated in the PhosphoRS algorithm.
     *
     * @param spectrum the spectrum studied
     * @param w the m/z range considered
     * @param d the m/z tolerance in daltons
     * @param nDecimals the number of decimals to use
     *
     * @return the probability p for a calculated fragment matching one of the
     * experimental masses by chance as estimated in the PhosphoRS algorithm.
     */
    private static double getp(Spectrum spectrum, double w, double d, int nDecimals) {
        if (w == 0.0) {
            return 1.0;
        }
        int N = spectrum.getPeakMap().size();
        if (N <= 1) {
            return 1.0;
        }
        double p = d * N / w;
        if (p > 1) {
            p = 1;
        }
        double roundedP = Util.floorDouble(p, nDecimals);
        return roundedP;
    }

    /**
     * Returns a map of the different possible peptides for the different
     * profiles.
     *
     * @param peptide the peptide of interest
     * @param ptms the PTMs to score
     * @param possibleProfiles the different profiles
     *
     * @return a map of the different peptides for the different profiles
     *
     * @throws IOException exception thrown whenever an error occurred while
     * reading a protein sequence
     * @throws InterruptedException exception thrown whenever an error occurred
     * while reading a protein sequence
     * @throws ClassNotFoundException if a ClassNotFoundException occurs
     * @throws SQLException if an SQLException occurs
     */
    private static HashMap<String, Peptide> getPossiblePeptidesMap(Peptide peptide, ArrayList<PTM> ptms, ArrayList<ArrayList<Integer>> possibleProfiles) throws IOException, SQLException, ClassNotFoundException, InterruptedException {

        String representativePTM = ptms.get(0).getName();
        HashMap<String, Peptide> result = new HashMap<String, Peptide>(possibleProfiles.size());
        int peptideLength = peptide.getSequence().length();
        for (ArrayList<Integer> profile : possibleProfiles) {
            Peptide tempPeptide = Peptide.getNoModPeptide(peptide, ptms);
            for (int pos : profile) {
                int index = pos;
                if (index == 0) {
                    index = 1;
                } else if (index == peptideLength + 1) {
                    index = peptideLength;
                }
                tempPeptide.addModificationMatch(new ModificationMatch(representativePTM, true, index));
            }
            String profileKey = KeyUtils.getKey(profile);
            result.put(profileKey, tempPeptide);
        }
        return result;
    }

    /**
     * Returns a map of the number of possible fragment ions for every peptide
     * indexed by the corresponding profile.
     *
     * @param possiblePeptides map of the possible peptides for every profile
     * @param possiblePeptideFragments the possible peptide fragments for every
     * peptide
     * @param spectrumAnnotator the spectrum annotator
     * @param scoringAnnotationSetttings the spectrum scoring annotation
     * settings
     *
     * @return a map of the number of possible fragment ions for every peptide
     */
    private static HashMap<String, Integer> getPossiblePeptideToN(HashMap<String, Peptide> possiblePeptides, HashMap<String, HashMap<Integer, HashMap<Integer, ArrayList<Ion>>>> possiblePeptideFragments, PeptideSpectrumAnnotator spectrumAnnotator, SpecificAnnotationSettings scoringAnnotationSetttings) {
        HashMap<String, Integer> result = new HashMap<String, Integer>(possiblePeptides.size());
        for (String profileKey : possiblePeptides.keySet()) {
            Peptide peptide = possiblePeptides.get(profileKey);
            HashMap<Integer, HashMap<Integer, ArrayList<Ion>>> fragmentsForProfile = possiblePeptideFragments.get(profileKey);
            HashMap<Integer, ArrayList<Ion>> expectedFragmentIons = spectrumAnnotator.getExpectedIons(scoringAnnotationSetttings, peptide, fragmentsForProfile);
            int n = 0;
            for (ArrayList<Ion> ions : expectedFragmentIons.values()) {
                n += ions.size();
            }
            result.put(profileKey, n);
        }
        return result;
    }

    /**
     * Returns a map of the possible ions for every peptide of every profile.
     *
     * @param possiblePeptides map of the possible peptides for every profile
     * @param scoringAnnotationSetttings the spectrum scoring annotation
     * settings
     *
     * @return a map of the possible ions for every peptide of every profile
     */
    private static HashMap<String, HashMap<Integer, HashMap<Integer, ArrayList<Ion>>>> getPossiblePeptideFragments(HashMap<String, Peptide> possiblePeptides, SpecificAnnotationSettings scoringAnnotationSetttings) {
        HashMap<String, HashMap<Integer, HashMap<Integer, ArrayList<Ion>>>> result = new HashMap<String, HashMap<Integer, HashMap<Integer, ArrayList<Ion>>>>(possiblePeptides.size());
        IonFactory fragmentFactory = IonFactory.getInstance();
        for (String profileKey : possiblePeptides.keySet()) {
            Peptide peptide = possiblePeptides.get(profileKey);
            HashMap<Integer, HashMap<Integer, ArrayList<Ion>>> possibleFragmentIons = fragmentFactory.getFragmentIons(peptide, scoringAnnotationSetttings);
            result.put(profileKey, possibleFragmentIons);
        }
        return result;
    }

    /**
     * Returns the possible modification profiles given the possible sites and
     * number of modifications. Sites are sorted in increasing order.
     *
     * @param possibleSites the possible modification sites in increasing order
     * @param nPtms the number of modifications
     *
     * @return a list of possible modification profiles
     */
    private static ArrayList<ArrayList<Integer>> getPossibleModificationProfiles(ArrayList<Integer> possibleSites, int nPtms) {

        ArrayList<ArrayList<Integer>> result = new ArrayList<ArrayList<Integer>>();

        for (int pos : possibleSites) {
            ArrayList<Integer> profile = new ArrayList<Integer>(nPtms);
            profile.add(pos);
            result.add(profile);
        }

        for (int i = 2; i <= nPtms; i++) {

            ArrayList<ArrayList<Integer>> resultAtI = new ArrayList<ArrayList<Integer>>((int) (1.5 * result.size()));

            for (ArrayList<Integer> previousProfile : result) {
                int lastPos = previousProfile.get(previousProfile.size() - 1);
                for (int pos : possibleSites) {
                    if (pos > lastPos) {
                        ArrayList<Integer> profile = new ArrayList<Integer>(previousProfile);
                        profile.add(pos);
                        resultAtI.add(profile);
                    }
                }
            }
            result = resultAtI;
        }

        return result;
    }

    /**
     * Returns a map of all potential site determining ions indexed by their
     * m/z.
     *
     * @param noModPeptide the version of the peptide which does not contain the
     * modification of interest
     * @param possibleProfiles the possible modification profiles to inspect
     * @param ptms the PTMs scored
     * @param spectrumAnnotator the spectrum annotator used throughout the
     * scoring
     * @param scoringAnnotationSetttings the annotation settings specific to
     * this peptide and spectrum
     *
     * @return a map of all potential site determining ions indexed by their m/z
     */
    private static HashMap<Double, ArrayList<String>> getSiteDeterminingIons(Peptide noModPeptide, ArrayList<ArrayList<Integer>> possibleProfiles, ArrayList<PTM> ptms, PeptideSpectrumAnnotator spectrumAnnotator, SpecificAnnotationSettings scoringAnnotationSetttings) {

        String sequence = noModPeptide.getSequence();
        Peptide peptide = new Peptide(sequence, noModPeptide.getModificationMatches());
        int sequenceLength = sequence.length();
        String representativePTM = ptms.get(0).getName();

        HashMap<Double, ArrayList<String>> siteDeterminingIons = new HashMap<Double, ArrayList<String>>();
        HashMap<Double, ArrayList<String>> commonIons = new HashMap<Double, ArrayList<String>>();

        for (ArrayList<Integer> modificationProfile : possibleProfiles) {

            for (int pos : modificationProfile) {
                int position;
                if (pos == 0) {
                    position = 1;
                } else if (pos == sequenceLength + 1) {
                    position = sequenceLength;
                } else {
                    position = pos;
                }
                peptide.addModificationMatch(new ModificationMatch(representativePTM, true, position));
            }

            HashSet<Double> mzs = new HashSet<Double>(2);

            for (ArrayList<Ion> ions : spectrumAnnotator.getExpectedIons(scoringAnnotationSetttings, peptide).values()) {
                for (Ion ion : ions) {
                    if (ion.getType() == Ion.IonType.PEPTIDE_FRAGMENT_ION) {
                        for (int charge : scoringAnnotationSetttings.getSelectedCharges()) {
                            double mz = ion.getTheoreticMz(charge);
                            mzs.add(mz);
                        }
                    }
                }
            }

            String profileKey = KeyUtils.getKey(modificationProfile);
            for (double mz : mzs) {
                if (commonIons.isEmpty()) {
                    ArrayList<String> profiles = new ArrayList<String>(2);
                    commonIons.put(mz, profiles);
                    profiles.add(profileKey);
                } else if (!commonIons.containsKey(mz)) {
                    ArrayList<String> profiles = siteDeterminingIons.get(mz);
                    if (profiles == null) {
                        profiles = new ArrayList<String>(2);
                        siteDeterminingIons.put(mz, profiles);
                    }
                    profiles.add(profileKey);
                }
            }

            for (double mz : new HashSet<Double>(commonIons.keySet())) {
                if (!mzs.contains(mz)) {
                    siteDeterminingIons.put(mz, commonIons.get(mz));
                    commonIons.remove(mz);
                } else {
                    commonIons.get(mz).add(profileKey);
                }
            }
        }

        return siteDeterminingIons;
    }

    /**
     * Returns a list of spectra containing only the most intense ions. The
     * index of the spectrum in the list corresponds to the increasing number of
     * peaks, ie the depth, starting with depth 1.
     *
     * @param spectrum the spectrum of interest
     *
     * @return a list of spectra containing only the most intense ions.
     */
    private static ArrayList<MSnSpectrum> getReducedSpectra(MSnSpectrum spectrum) {

        if (spectrum.isEmpty()) {
            throw new IllegalArgumentException("Attempting to extract peaks from an empty spectrum.");
        }

        ArrayList<MSnSpectrum> reducedSpectra = new ArrayList<MSnSpectrum>(MAX_DEPTH);
        HashMap<Double, ArrayList<Peak>> intensityToPeakMap = new HashMap<Double, ArrayList<Peak>>(spectrum.getPeakMap().size());

        for (Peak peak : spectrum.getPeakList()) {
            double intensity = peak.intensity;
            ArrayList<Peak> peaks = intensityToPeakMap.get(intensity);
            if (peaks == null) {
                peaks = new ArrayList<Peak>();
                intensityToPeakMap.put(intensity, peaks);
            }
            peaks.add(peak);
        }

        ArrayList<Double> intensities = new ArrayList<Double>(intensityToPeakMap.keySet());
        Collections.sort(intensities, Collections.reverseOrder());
        int depth = 0;

        HashMap<Double, Peak> mzToPeak = new HashMap<Double, Peak>(1);

        for (double intensity : intensities) {
            for (Peak peak : intensityToPeakMap.get(intensity)) {
                mzToPeak.put(peak.mz, peak);
                depth++;
                reducedSpectra.add(new MSnSpectrum(spectrum.getLevel(), spectrum.getPrecursor(), spectrum.getSpectrumTitle() + "_" + depth, mzToPeak, spectrum.getFileName()));
                if (depth > MAX_DEPTH) {
                    break;
                }
                HashMap<Double, Peak> newMzToPeak = new HashMap<Double, Peak>(depth + 1);
                newMzToPeak.putAll(mzToPeak);
                mzToPeak = newMzToPeak;
            }
            if (depth > MAX_DEPTH) {
                break;
            }
        }

        return reducedSpectra;
    }

    /**
     * Filters the spectrum so that p is lower or equal to 1 by retaining the
     * most intense peaks in a window of 10 times the ms2 tolerance.
     *
     * @param spectrum the original spectrum
     * @param scoringAnnotationSetttings the annotation settings
     *
     * @return the filtered spectrum
     *
     * @throws java.lang.InterruptedException exception thrown if the thread is
     * interrupted
     */
    private static MSnSpectrum filterSpectrum(MSnSpectrum spectrum, SpecificAnnotationSettings scoringAnnotationSetttings) throws InterruptedException {

        Double window;
        Integer maxPeaks;

        double ms2Tolerance = scoringAnnotationSetttings.getFragmentIonAccuracyInDa(spectrum.getMaxMz());

        if (ms2Tolerance <= 10) {
            window = 10 * ms2Tolerance;
            maxPeaks = 10;
        } else {
            window = WINDOW_SIZE;
            maxPeaks = (int) (window / ms2Tolerance);
        }

        if (maxPeaks < 1) {
            throw new IllegalArgumentException("All peaks removed by filtering.");
        }

        HashMap<Double, Peak> peakMap = spectrum.getPeakMap(),
                newMap = new HashMap<Double, Peak>(peakMap.size()),
                tempMap = new HashMap<Double, Peak>();
        Double refMz = null;

        for (Double mz : spectrum.getOrderedMzValues()) {
            if (refMz == null) {
                refMz = mz;
            } else if (mz > refMz + window) {
                if (tempMap.size() <= maxPeaks) {
                    newMap.putAll(tempMap);
                    tempMap.clear();
                } else {
                    ArrayList<Double> intensities = new ArrayList<Double>(tempMap.keySet());
                    Collections.sort(intensities, Collections.reverseOrder());
                    for (int i = 0; i < Math.min(intensities.size(), maxPeaks); i++) {
                        Double intensity = intensities.get(i);
                        Peak peak = tempMap.get(intensity);
                        newMap.put(peak.mz, peak);
                    }
                    tempMap.clear();
                }
                refMz += window;
            }
            Peak peak = peakMap.get(mz);
            tempMap.put(peak.intensity, peak);
        }

        ArrayList<Double> intensities = new ArrayList<Double>(tempMap.keySet());
        Collections.sort(intensities, Collections.reverseOrder());

        for (int i = 0; i < Math.min(intensities.size(), maxPeaks); i++) {
            Double intensity = intensities.get(i);
            Peak peak = tempMap.get(intensity);
            newMap.put(peak.mz, peak);
        }

        return new MSnSpectrum(spectrum.getLevel(), spectrum.getPrecursor(), spectrum.getSpectrumTitle() + "_filtered", newMap, spectrum.getFileName());
    }
}
