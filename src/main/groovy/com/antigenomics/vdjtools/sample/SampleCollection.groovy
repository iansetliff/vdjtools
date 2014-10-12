/**
 Copyright 2014 Mikhail Shugay (mikhail.shugay@gmail.com)

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package com.antigenomics.vdjtools.sample

import com.antigenomics.vdjtools.Clonotype
import com.antigenomics.vdjtools.Software
import com.antigenomics.vdjtools.intersection.IntersectionType
import com.antigenomics.vdjtools.intersection.IntersectionUtil
import com.antigenomics.vdjtools.sample.metadata.MetadataTable
import com.antigenomics.vdjtools.timecourse.DynamicClonotype
import com.antigenomics.vdjtools.timecourse.TimeCourse
import org.apache.commons.io.FilenameUtils

/**
 * Base class to store and handle collections of samples in VDJtools
 * Provides loading samples and their metadata
 * The only limitation is that samples should be processed by same software
 */
class SampleCollection implements Iterable<Sample> {
    private final Map<String, SampleConnection> sampleMap = new HashMap<>()
    private final Software software
    private final boolean strict, lazy, store

    private final MetadataTable metadataTable

    /**
     * Gets a metadata table that allows querying and ordering of samples in this collection
     * @return metadata table
     */
    MetadataTable getMetadataTable() {
        metadataTable
    }

    /**
     * Builds a sample collection from a pre-defined list of samples.
     * Samples should belong to the same metadata table, sample order will be preserved.
     * @param samples list of samples
     */
    SampleCollection(List<Sample> samples) {
        this.software = null
        this.strict = true
        this.lazy = false
        this.store = true
        this.metadataTable = samples[0].sampleMetadata.parent
        samples.each {
            if (it.sampleMetadata.parent != metadataTable)
                throw new Exception("Only samples coming from same metadata table are allowed")
            def sampleId = it.sampleMetadata.sampleId
            sampleMap.put(sampleId, new DummySampleConnection(it))
        }
    }

    /**
     * Builds a sample collection from a pre-defined list of sample file names.
     * Samples will be assigned to generic metadata table, sample order will be preserved.
     * @param sampleFileNames list of sample file names
     * @param store if set to true, all loaded samples will be stored in memory (only has effect if lazy is set to true)
     * @param lazy if set to true, all samples will be immediately loaded, otherwise samples will be loaded by request
     * @param strict if set to false, will ignore samples with missing files, otherwise will throw an exception in such case
     */
    SampleCollection(List<String> sampleFileNames, Software software,
                     boolean store, boolean lazy, boolean strict) {
        this.software = software
        this.strict = strict
        this.lazy = lazy
        this.store = store
        this.metadataTable = new MetadataTable()
        sampleFileNames.each { String fileName ->
            if (new File(fileName).exists()) {
                def sampleId = FilenameUtils.getBaseName(fileName)
                def sampleMetadata = metadataTable.createRow(sampleId)
                sampleMap.put(sampleId, new SampleStreamConnection(new FileInputStream(fileName),
                        sampleMetadata, software, lazy, store))
            } else if (strict) {
                throw new FileNotFoundException("Missing sample file $fileName")
            } else {
                println "[${new Date()} SampleCollection] WARNING: File $fileName not found, skipping"
            }
        }
    }

    /**
     * Builds a sample collection from a pre-defined list of sample file names.
     * Samples will be assigned to generic metadata table, sample order will be preserved.
     * @param sampleFileNames list of sample file names
     * @param store if set to true, all loaded samples will be stored in memory (only has effect if lazy is set to true)
     * @param lazy if set to true, all samples will be immediately loaded, otherwise samples will be loaded by request
     */
    SampleCollection(List<String> sampleFileNames, Software software,
                     boolean store, boolean lazy) {
        this(sampleFileNames, software, store, lazy, true)
    }

    /**
     * Builds a sample collection from a pre-defined list of sample file names.
     * Samples will be assigned to generic metadata table, sample order will be preserved.
     * @param sampleFileNames list of sample file names
     * @param store if set to true, all loaded samples will be stored in memory
     */
    SampleCollection(List<String> sampleFileNames, Software software,
                     boolean store) {
        this(sampleFileNames, software, store, true, true)
    }

    /**
     * Builds a sample collection from a pre-defined list of sample file names.
     * Samples will be assigned to generic metadata table, sample order will be preserved.
     * @param sampleFileNames list of sample file names
     */
    SampleCollection(List<String> sampleFileNames, Software software) {
        this(sampleFileNames, software, false, true, true)
    }

    /**
     * Loads a sample collection using custom metadata file.
     * File should contain two columns: first with file path and second with sample id
     * Additional columns will be stored as metadata entries.
     * First line of file should contain header that includes metadata field names.
     * Samples will be ordered as they appear in file
     * @param sampleMetadataFileName metadata file path
     * @param software software used to get processed samples
     * @param store if set to true, all loaded samples will be stored in memory (only has effect if lazy is set to true)
     * @param lazy if set to true, all samples will be immediately loaded, otherwise samples will be loaded by request
     * @param strict if set to false, will ignore samples with missing files, otherwise will throw an exception in such case
     */
    SampleCollection(String sampleMetadataFileName, Software software,
                     boolean store, boolean lazy, boolean strict) {
        this.software = software

        this.store = store
        this.lazy = lazy
        this.strict = strict

        def metadataTable = null

        new File(sampleMetadataFileName).withReader { reader ->
            metadataTable = new MetadataTable(reader.readLine().split("\t")[2..-1])

            def line, splitLine
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("#")) {
                    splitLine = line.split("\t")
                    String fileName = splitLine[0], sampleId = splitLine[1]

                    def entries = splitLine.length > 2 ? splitLine[2..-1] : []

                    if (new File(fileName).exists()) {
                        def sampleMetadata = metadataTable.createRow(sampleId, entries)
                        sampleMap.put(sampleId, new SampleStreamConnection(new FileInputStream(fileName),
                                sampleMetadata, software, lazy, store))
                    } else if (strict) {
                        throw new FileNotFoundException("Missing sample file $fileName")
                    } else {
                        println "[${new Date()} SampleCollection] WARNING: File $fileName not found, skipping"
                    }
                }
            }
        }

        this.metadataTable = metadataTable
    }

    /**
     * Loads a sample collection using custom metadata file.
     * File should contain two columns: first with file path and second with sample id
     * Additional columns will be stored as metadata entries.
     * First line of file should contain header that includes metadata field names.
     * Samples will be ordered as they appear in file
     * @param sampleMetadataFileName metadata file path
     * @param software software used to get processed samples
     * @param store if set to true, all loaded samples will be stored in memory (only has effect if lazy is set to true)
     * @param lazy if set to true, all samples will be immediately loaded, otherwise samples will be loaded by request
     */
    SampleCollection(String sampleMetadataFileName, Software software,
                     boolean store, boolean lazy) {
        this(sampleMetadataFileName, software, store, lazy, true)
    }

    /**
     * Loads a sample collection using custom metadata file.
     * File should contain two columns: first with file path and second with sample id
     * Additional columns will be stored as metadata entries.
     * First line of file should contain header that includes metadata field names.
     * Samples will be ordered as they appear in file
     * @param sampleMetadataFileName metadata file path
     * @param software software used to get processed samples
     * @param store if set to true, all loaded samples will be stored in memory
     */
    SampleCollection(String sampleMetadataFileName, Software software,
                     boolean store) {
        this(sampleMetadataFileName, software, store, true, true)
    }

    /**
     * Loads a sample collection using custom metadata file.
     * File should contain two columns: first with file path and second with sample id
     * Additional columns will be stored as metadata entries.
     * First line of file should contain header that includes metadata field names.
     * Samples will be ordered as they appear in file
     * @param sampleMetadataFileName metadata file path
     * @param software software used to get processed samples
     */
    SampleCollection(String sampleMetadataFileName, Software software) {
        this(sampleMetadataFileName, software, false, true, true)
    }

    /**
     * Generates a time course for a given sequential intersection.
     * Only clonotypes met in at least two sequential samples are retained
     * @return clonotype abundance time course
     */
    TimeCourse asTimeCourse() {
        // todo: REWRITE
        def clonotypeMap = new HashMap<String, Clonotype[]>()

        def intersectionUtil = new IntersectionUtil(IntersectionType.NucleotideV)

        this.eachWithIndex { Sample sample, int sampleIndex ->
            sample.eachWithIndex { Clonotype clonotype, int i ->

                def key = intersectionUtil.generateKey(clonotype)
                def entry = clonotypeMap[key]
                if (!entry)
                    clonotypeMap.put(key, entry = new Clonotype[size()])

                entry[sampleIndex] = clonotype
            }
        }

        new TimeCourse(sampleMap.values() as Sample[], clonotypeMap.values().collect { new DynamicClonotype(it) })
    }

    /**
     * Lists all unique sample pairs in a given collection.
     * Pairs (i, j) are chosen such as j > i, no (i, i) pairs allowed.
     * @return a list of sample pairs
     */
    List<SamplePair> listPairs() {
        def samplePairs = new ArrayList()

        for (int i = 0; i < size(); i++)
            for (int j = i + 1; j < size(); j++)
                samplePairs.add(this[i, j])

        samplePairs
    }

    /**
     * Get sample by id
     * @param sampleId sample id
     * @return sample
     */
    Sample getAt(String sampleId) {
        sampleMap[sampleId]
    }

    /**
     * Get sample by index, according to current ordering
     * @param i sample index
     * @return sample
     */
    Sample getAt(int i) {
        if (i < 0 || i >= metadataTable.sampleCount)
            throw new IndexOutOfBoundsException()

        sampleMap[metadataTable.getRow(i).sampleId].sample
    }

    /**
     * Gets sample pair by indices, according to current ordering
     * @param i index of first sample in pair
     * @param j index of second sample in pair
     * @return sample pair
     */
    SamplePair getAt(int i, int j) {
        new SamplePair(sampleMap[metadataTable.getRow(i).sampleId],
                sampleMap[metadataTable.getRow(j).sampleId],
                i, j)
    }

    Iterator iterator() {
        def iter = metadataTable.sampleIterator
        return [
                hasNext: {
                    iter.hasNext()
                },
                next   : {
                    def sampleId = iter.next()
                    sampleMap[sampleId].sample
                }] as Iterator
    }

    /**
     * Gets the number of samples
     * @return number of samples
     */
    int size() {
        sampleMap.size()
    }

    @Override
    String toString() {
        "samples=" + this.collect { it.sampleMetadata.sampleId }.join(",") +
                "\nmetadata=" + metadataTable.columnIterator.collect().join(",")
    }
}
