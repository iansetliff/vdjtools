/*
 * Copyright 2013-2015 Mikhail Shugay (mikhail.shugay@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.antigenomics.vdjtools.profile;

import com.milaboratory.core.sequence.AminoAcidSequence;

public class AminoAcidProfileBuilder {
    private final int nBins;
    private final AminoAcidProfileBin[] aminoAcidProfileBins;

    public AminoAcidProfileBuilder(int nBins, AminoAcidPropertyGroup... aminoAcidPropertyGroups) {
        this.nBins = nBins;
        this.aminoAcidProfileBins = new AminoAcidProfileBin[nBins];
        for (int i = 0; i < nBins; i++) {
            aminoAcidProfileBins[i] = new AminoAcidProfileBin(aminoAcidPropertyGroups);
        }
    }

    public void update(AminoAcidSequence aminoAcidSequence) {
        update(aminoAcidSequence, 1);
    }

    public void update(AminoAcidSequence aminoAcidSequence, int weight) {
        int n = aminoAcidSequence.size();

        for (int i = 0; i < n; i++) {
            int bin = (int) ((i / (double) n) * nBins);

            update(bin, aminoAcidSequence.codeAt(i), weight);
        }
    }

    private void update(int bin, byte code, int weight) {
        aminoAcidProfileBins[bin].update(code, weight);
    }
}