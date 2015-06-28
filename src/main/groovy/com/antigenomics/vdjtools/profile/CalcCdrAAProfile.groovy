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

package com.antigenomics.vdjtools.profile

import com.antigenomics.vdjtools.sample.Sample
import com.antigenomics.vdjtools.sample.SampleCollection
import com.antigenomics.vdjtools.sample.metadata.MetadataTable

import static com.antigenomics.vdjtools.util.ExecUtil.formOutputPath

def DEFAULT_AA_GROUPS = BasicAminoAcidProperties.INSTANCE.groupNames.join(","),
    DEFAULT_BINNING = "CDR3-full:9,V-germ:3,D-germ:1,J-germ:3,VD-junc:1,DJ-junc:1,VJ-junc:3"

def cli = new CliBuilder(usage: "CalcCdrAAProfile [options] " +
        "[sample1 sample2 sample3 ... if -m is not specified] output_prefix")
cli.h("display help message")
cli.m(longOpt: "metadata", argName: "filename", args: 1,
        "Metadata file. First and second columns should contain file name and sample id. " +
                "Header is mandatory and will be used to assign column names for metadata.")
cli.u(longOpt: "unweighted", "Will count each clonotype only once, apart from conventional frequency-weighted histogram.")
cli.g(longOpt: "group-list", argName: "group1,...", args: 1,
        "Comma-separated list of amino-acid property groups to analyze. " +
                "Allowed values: $DEFAULT_AA_GROUPS. " +
                "[default = use all]")
cli.b(longOpt: "segment-bins", argName: "segment1:nbins1,...", args: 1,
        "Bin by segment (V, J segment part and either D segment, V-D, D-J junction or V-J junction). " +
                "[default = $DEFAULT_BINNING]")


def opt = cli.parse(args)

if (opt == null)
    System.exit(-1)

if (opt.h || opt.arguments().size() == 0) {
    cli.usage()
    System.exit(-1)
}

// Check if metadata is provided

def metadataFileName = opt.m

if (metadataFileName ? opt.arguments().size() != 1 : opt.arguments().size() < 2) {
    if (metadataFileName)
        println "Only output prefix should be provided in case of -m"
    else
        println "At least 1 sample files should be provided if not using -m"
    cli.usage()
    System.exit(-1)
}

// Remaining arguments

def knownRegions = [new VGermline(), new DGermline(), new JGermline(),
                    new VDJunction(), new DJJunction(),
                    new VJJunction(), new FullCdr3()].collectEntries {
    [(it.name): it]
}

def getRegionByName = { String name ->
    if (!knownRegions.containsKey(name)) {
        println "[ERROR] Unknown region $name, allowed values are: ${knownRegions.keySet()}"
        System.exit(-1)
    }
    knownRegions[name]
}

def outputFilePrefix = opt.arguments()[-1],
    unweighted = (boolean) opt.u,
    binning = (opt.b ?: DEFAULT_BINNING).split(",").collectEntries {
        def split2 = it.split(":")
        [(getRegionByName(split2[0])): split2[1].toInteger()]
    },
    propertyGroups = (opt.g ?: DEFAULT_AA_GROUPS).split(",")

def scriptName = getClass().canonicalName.split("\\.")[-1]

//
// Batch load all samples (lazy)
//

println "[${new Date()} $scriptName] Reading sample(s)"

def sampleCollection = metadataFileName ?
        new SampleCollection((String) metadataFileName) :
        new SampleCollection(opt.arguments()[0..-2])

println "[${new Date()} $scriptName] ${sampleCollection.size()} sample(s) prepared"

//
// Compute and output diversity measures, spectratype, etc
//

def profileBuilder = new Cdr3AAProfileBuilder(binning, !unweighted, propertyGroups)

new File(formOutputPath(outputFilePrefix, "cdr3aa.profile")).withPrintWriter { pw ->
    def header = "#$MetadataTable.SAMPLE_ID_COLUMN\t" +
            sampleCollection.metadataTable.columnHeader + "\t" +
            "cdr3.segment\tbin\tproperty.group\tproperty\tcount\ttotal"

    pw.println(header)

    def sampleCounter = 0

    sampleCollection.each { Sample sample ->
        def profiles = profileBuilder.create(sample)

        println "[${new Date()} $scriptName] ${++sampleCounter} sample(s) processed"

        profiles.each { profileEntry ->
            def segmentName = profileEntry.key.name
            profileEntry.value.bins.each { bin ->
                bin.summary.each { groupEntry ->
                    def groupName = groupEntry.key
                    groupEntry.value.each { propertyEntry ->
                        pw.println([sample.sampleMetadata.sampleId, sample.sampleMetadata,
                                    segmentName, bin.index, groupName, propertyEntry.key,
                                    propertyEntry.value, bin.total].join("\t"))
                    }
                }
            }
        }
    }
}

println "[${new Date()} $scriptName] Finished"