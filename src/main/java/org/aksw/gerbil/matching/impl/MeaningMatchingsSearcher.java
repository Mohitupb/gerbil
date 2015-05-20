package org.aksw.gerbil.matching.impl;

import java.util.List;
import java.util.Set;

import org.aksw.gerbil.semantic.kb.UriKBClassifier;
import org.aksw.gerbil.semantic.sameas.MultipleSameAsRetriever;
import org.aksw.gerbil.semantic.sameas.SameAsRetriever;
import org.aksw.gerbil.transfer.nif.Meaning;
import org.aksw.gerbil.transfer.nif.MeaningEqualityChecker;

import com.carrotsearch.hppc.BitSet;

/**
 * This implementation of a {@link MatchingsSearcher} searches for a matching
 * meaning for every given expected meaning and a list of annotator results.
 * 
 * TODO This class could be further enhanced. For every expected URI the
 * annotator result URIs are extended. This could be done outside of this
 * method.
 * 
 * @author Michael R&ouml;der <roeder@informatik.uni-leipzig.de>
 * 
 * @param <T>
 *            is the {@link Meaning} class or one of its extensions.
 */
public class MeaningMatchingsSearcher<T extends Meaning> implements MatchingsSearcher<T> {

    private SameAsRetriever datasetSameAsRetriever;
    private SameAsRetriever annotatorSameAsRetriever;
    private UriKBClassifier uriKBClassifier;

    public MeaningMatchingsSearcher() {
    }

    public MeaningMatchingsSearcher(UriKBClassifier uriKBClassifier) {
        this.uriKBClassifier = uriKBClassifier;
    }

    public MeaningMatchingsSearcher(UriKBClassifier uriKBClassifier, SameAsRetriever globalSameAsRestriever,
            SameAsRetriever datasetSameAsRetriever, SameAsRetriever annotatorSameAsRetriever) {
        if (globalSameAsRestriever != null) {
            if (datasetSameAsRetriever != null) {
                this.datasetSameAsRetriever = new MultipleSameAsRetriever(datasetSameAsRetriever,
                        globalSameAsRestriever);
            } else {
                this.datasetSameAsRetriever = globalSameAsRestriever;
            }
            if (annotatorSameAsRetriever != null) {
                this.annotatorSameAsRetriever = new MultipleSameAsRetriever(annotatorSameAsRetriever,
                        globalSameAsRestriever);
            } else {
                this.annotatorSameAsRetriever = globalSameAsRestriever;
            }
        } else {
            this.datasetSameAsRetriever = datasetSameAsRetriever;
            this.annotatorSameAsRetriever = annotatorSameAsRetriever;
        }
        this.uriKBClassifier = uriKBClassifier;
    }

    @Override
    public BitSet findMatchings(T expectedElement, List<T> annotatorResults, BitSet alreadyUsedResults) {
        BitSet matching = new BitSet(alreadyUsedResults.size());
        Set<String> extendedUris = expectedElement.getUris();
        Set<String> extendedAnnotResult = null;
        boolean expectingKBUri, annotatorHasKBUri;

        // Extend the expected result
        if (datasetSameAsRetriever != null) {
            datasetSameAsRetriever.addSameURIs(extendedUris);
        }
        // Check whether a link to a known KB is expected
        expectingKBUri = (uriKBClassifier == null) ? true : uriKBClassifier.containsKBUri(extendedUris);

        T annotatorResult;
        for (int i = 0; i < annotatorResults.size(); ++i) {
            if (!alreadyUsedResults.get(i)) {
                annotatorResult = annotatorResults.get(i);
                // extend the annotator result, if possible
                extendedAnnotResult = annotatorResult.getUris();
                if (annotatorSameAsRetriever != null) {
                    annotatorSameAsRetriever.addSameURIs(extendedAnnotResult);
                }
                annotatorHasKBUri = (uriKBClassifier == null) ? true : uriKBClassifier
                        .containsKBUri(extendedAnnotResult);
                // if both can be mapped to a KB
                if ((annotatorHasKBUri) && (expectingKBUri)) {
                    // if the sets are intersecting
                    if (MeaningEqualityChecker.overlaps(extendedUris, extendedAnnotResult)) {
                        matching.set(i);
                    }
                    // else if both are not mapped to a KB
                } else if ((!annotatorHasKBUri) && (!expectingKBUri)) {
                    matching.set(i);
                }
            }
        }
        return matching;
    }
}
