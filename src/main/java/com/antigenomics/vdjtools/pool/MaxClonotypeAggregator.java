/*
 * Copyright 2013-2014 Mikhail Shugay (mikhail.shugay@gmail.com)
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
 *
 * Last modified on 2.11.2014 by mikesh
 */

package com.antigenomics.vdjtools.pool;

import com.antigenomics.vdjtools.Clonotype;

public class MaxClonotypeAggregator extends ClonotypeAggregator {
    private double maxFreq;

    public MaxClonotypeAggregator(Clonotype clonotype, int sampleId) {
        super(clonotype, sampleId);
        this.maxFreq = clonotype.getFreq();
    }

    @Override
    protected boolean _combine(Clonotype other, int sampleId) {
        double freq = other.getFreq();
        if (maxFreq < freq) {
            this.maxFreq = freq;
            return true;
        }
        return false;
    }

    public double getMaxFreq() {
        return maxFreq;
    }
}
