package org.matonto.ontology.core.impl.owlapi;

/*-
 * #%L
 * org.matonto.ontology.core.impl.owlapi
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2016 - 2017 iNovex Information Systems, Inc.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.matonto.ontology.core.api.Individual;
import org.matonto.ontology.core.api.Ontology;
import org.matonto.ontology.core.api.OntologyId;
import org.matonto.ontology.core.api.OntologyManager;
import org.matonto.ontology.core.api.classexpression.OClass;
import org.matonto.ontology.core.api.propertyexpression.DataProperty;
import org.matonto.ontology.core.api.propertyexpression.ObjectProperty;
import org.matonto.ontology.core.impl.owlapi.classexpression.SimpleClass;
import org.matonto.ontology.core.impl.owlapi.propertyExpression.SimpleDataProperty;
import org.matonto.ontology.core.impl.owlapi.propertyExpression.SimpleObjectProperty;
import org.matonto.persistence.utils.api.SesameTransformer;
import org.matonto.rdf.api.IRI;
import org.matonto.rdf.api.Resource;
import org.matonto.rdf.api.ValueFactory;
import org.matonto.rdf.core.impl.sesame.SimpleValueFactory;
import org.matonto.vocabularies.xsd.XSD;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openrdf.model.Model;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.Rio;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.RioRDFXMLDocumentFormatFactory;
import org.semanticweb.owlapi.model.MissingImportHandlingStrategy;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyLoaderConfiguration;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.rio.RioMemoryTripleSource;
import org.semanticweb.owlapi.rio.RioParserImpl;

import java.io.InputStream;
import java.util.Optional;
import java.util.Set;

public class FullSimpleOntologyTest {
    private ValueFactory vf;
    private IRI classIRI;
    private IRI classIRIC;
    private IRI classIRID;
    private IRI classIRIE;
    private IRI dataProp1IRI;
    private IRI dataProp2IRI;
    private IRI objectProp1IRI;
    private IRI objectProp2IRI;
    private IRI errorIRI;
    private IRI importedIRI0;
    private IRI importedIRI;
    private Ontology ontology;

    @Mock
    private OntologyManager ontologyManager;

    @Mock
    private OntologyId ontologyId;

    @Mock
    SesameTransformer transformer;

    @Before
    public void setUp() {
        vf = SimpleValueFactory.getInstance();
        IRI ontologyIRI = vf.createIRI("http://test.com/ontology1");
        IRI versionIRI = vf.createIRI("http://test.com/ontology1/1.0.0");
        classIRI = vf.createIRI("http://test.com/ontology1#TestClassA");
        classIRIC = vf.createIRI("http://test.com/ontology1#TestClassC");
        classIRID = vf.createIRI("http://test.com/ontology1#TestClassD");
        classIRIE = vf.createIRI("http://test.com/ontology1#TestClassE");
        dataProp1IRI = vf.createIRI("http://test.com/ontology1#testDataProperty1");
        dataProp2IRI = vf.createIRI("http://test.com/ontology1#testDataProperty2");
        objectProp1IRI = vf.createIRI("http://test.com/ontology1#testObjectProperty1");
        objectProp2IRI = vf.createIRI("http://test.com/ontology1#testObjectProperty2");
        errorIRI = vf.createIRI("http://test.com/ontology1#error");
        importedIRI0 = vf.createIRI("http://matonto.org/ontology/test-local-imports-1#Class0");
        importedIRI = vf.createIRI("http://matonto.org/ontology/test-local-imports-1#Class1");
        SimpleOntologyValues values = new SimpleOntologyValues();
        values.setValueFactory(vf);

        MockitoAnnotations.initMocks(this);

        when(ontologyId.getOntologyIRI()).thenReturn(Optional.of(ontologyIRI));
        when(ontologyId.getVersionIRI()).thenReturn(Optional.of(versionIRI));
        when(ontologyManager.createOntologyId(any(IRI.class), any(IRI.class))).thenReturn(ontologyId);
        when(ontologyManager.createOntologyId(any(IRI.class))).thenReturn(ontologyId);
        when(ontologyManager.getOntologyRecordResource(any(Resource.class))).thenReturn(Optional.empty());

        InputStream stream = this.getClass().getResourceAsStream("/test.owl");
        ontology = new SimpleOntology(stream, ontologyManager, transformer);

        values.setOntologyManager(ontologyManager);
        values.setTransformer(transformer);
    }

    @Test
    public void getImportedOntologyIRIsTest() throws Exception {
        // Setup:
        InputStream stream = this.getClass().getResourceAsStream("/test-imports.owl");
        Ontology ont = new SimpleOntology(stream, ontologyManager, transformer);

        Set<IRI> iris = ont.getImportedOntologyIRIs();
        assertEquals(2, iris.size());
        assertTrue(iris.contains(vf.createIRI("http://xmlns.com/foaf/0.1")));
    }

    @Test
    public void getImportsClosureFromStreamTest() throws Exception {
        // Setup:
        InputStream stream = this.getClass().getResourceAsStream("/test-imports.owl");
        Ontology ont = new SimpleOntology(stream, ontologyManager, transformer);

        Set<Ontology> ontologies = ont.getImportsClosure();
        assertEquals(5, ontologies.size());
    }

    @Test
    public void getImportsClosureFromModelTest() throws Exception {
        // Setup:
        InputStream stream = this.getClass().getResourceAsStream("/test-imports.owl");

        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntology ontology = manager.createOntology();

        Model sesameModel = Rio.parse(stream, "", RDFFormat.RDFXML);

        OWLOntologyLoaderConfiguration config = new OWLOntologyLoaderConfiguration()
                .setMissingImportHandlingStrategy(MissingImportHandlingStrategy.SILENT);
        RioParserImpl parser = new RioParserImpl(new RioRDFXMLDocumentFormatFactory());
        parser.parse(new RioMemoryTripleSource(sesameModel), ontology, config);
        Ontology ont = new SimpleOntology(ontology, null, ontologyManager, transformer);

        Set<Ontology> ontologies = ont.getImportsClosure();
        assertEquals(5, ontologies.size());
    }

    @Test
    public void getImportsClosureWithLocalImportsTest() throws Exception {
        // Setup:
        Resource ont3IRI = vf.createIRI("http://matonto.org/ontology/test-local-imports-3");
        Resource ont3RecordIRI = vf.createIRI("https://matonto.org/record/test-local-imports-3");
        InputStream stream3 = this.getClass().getResourceAsStream("/test-local-imports-3.ttl");
        Ontology ont3 = new SimpleOntology(stream3, ontologyManager, transformer);
        when(ontologyManager.getOntologyRecordResource(ont3IRI)).thenReturn(Optional.of(ont3RecordIRI));
        when(ontologyManager.retrieveOntology(ont3RecordIRI)).thenReturn(Optional.of(ont3));

        Resource ont2IRI = vf.createIRI("http://matonto.org/ontology/test-local-imports-2");
        Resource ont2RecordIRI = vf.createIRI("https://matonto.org/record/test-local-imports-2");
        InputStream stream2 = this.getClass().getResourceAsStream("/test-local-imports-2.ttl");
        Ontology ont2 = new SimpleOntology(stream2, ontologyManager, transformer);
        when(ontologyManager.getOntologyRecordResource(ont2IRI)).thenReturn(Optional.of(ont2RecordIRI));
        when(ontologyManager.retrieveOntology(ont2RecordIRI)).thenReturn(Optional.of(ont2));

        InputStream stream1 = this.getClass().getResourceAsStream("/test-local-imports-1.ttl");
        Ontology ont1 = new SimpleOntology(stream1, ontologyManager, transformer);

        Set<Ontology> ontologies = ont1.getImportsClosure();
        assertEquals(3, ontologies.size());
    }

    @Test
    public void getDataPropertyTest() throws Exception {
        Optional<DataProperty> optional = ontology.getDataProperty(dataProp1IRI);
        assertTrue(optional.isPresent());
        assertEquals(dataProp1IRI, optional.get().getIRI());
    }

    @Test
    public void getMissingDataPropertyTest() throws Exception {
        Optional<DataProperty> optional = ontology.getDataProperty(errorIRI);
        assertFalse(optional.isPresent());
    }

    @Test
    public void getDataPropertyRangeTest() throws Exception {
        // Setup:
        DataProperty dataProperty = new SimpleDataProperty(dataProp1IRI);

        Set<Resource> ranges = ontology.getDataPropertyRange(dataProperty);
        assertEquals(1, ranges.size());
        assertTrue(ranges.contains(vf.createIRI(XSD.INTEGER)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void getMissingDataPropertyRangeTest() throws Exception {
        // Setup:
        DataProperty dataProperty = new SimpleDataProperty(errorIRI);

        ontology.getDataPropertyRange(dataProperty);
    }

    @Test
    public void getDataPropertyRangeWithNonDatatypeTest() throws Exception {
        // Setup:
        DataProperty dataProperty = new SimpleDataProperty(dataProp2IRI);

        Set<Resource> ranges = ontology.getDataPropertyRange(dataProperty);
        assertEquals(1, ranges.size());
    }

    @Test
    public void getObjectPropertyTest() throws Exception {
        Optional<ObjectProperty> optional = ontology.getObjectProperty(objectProp1IRI);
        assertTrue(optional.isPresent());
        assertEquals(objectProp1IRI, optional.get().getIRI());
    }

    @Test
    public void getMissingObjectPropertyTest() throws Exception {
        Optional<ObjectProperty> optional = ontology.getObjectProperty(errorIRI);
        assertFalse(optional.isPresent());
    }

    @Test
    public void getObjectPropertyRangeTest() throws Exception {
        // Setup:
        ObjectProperty objectProperty = new SimpleObjectProperty(objectProp1IRI);

        Set<Resource> ranges = ontology.getObjectPropertyRange(objectProperty);
        assertEquals(1, ranges.size());
        assertTrue(ranges.contains(classIRI));
    }

    @Test(expected = IllegalArgumentException.class)
    public void getMissingObjectPropertyRangeTest() throws Exception {
        // Setup:
        ObjectProperty objectProperty = new SimpleObjectProperty(errorIRI);

        ontology.getObjectPropertyRange(objectProperty);
    }

    @Test
    public void getObjectPropertyRangeWithNonClassTest() throws Exception {
        // Setup:
        ObjectProperty objectProperty = new SimpleObjectProperty(objectProp2IRI);

        Set<Resource> ranges = ontology.getObjectPropertyRange(objectProperty);
        assertEquals(1, ranges.size());
    }

    @Test
    public void getIndividualsOfTypeIRITest() throws Exception {
        Set<Individual> individuals = ontology.getIndividualsOfType(classIRI);
        assertEquals(1, individuals.size());
    }

    @Test
    public void getIndividualsOfSubClassTypeIRITest() throws Exception {
        Set<Individual> individuals = ontology.getIndividualsOfType(classIRIC);
        assertEquals(1, individuals.size());
    }

    @Test
    public void getIndividualsOfTypeTest() throws Exception {
        // Setup:
        OClass clazz = new SimpleClass(classIRI);

        Set<Individual> individuals = ontology.getIndividualsOfType(clazz);
        assertEquals(1, individuals.size());
    }

    @Test
    public void getIndividualsOfSubClassTypeTest() throws Exception {
        // Setup:
        OClass clazz = new SimpleClass(classIRIC);

        Set<Individual> individuals = ontology.getIndividualsOfType(clazz);
        assertEquals(1, individuals.size());
    }

    @Test
    public void containsClassTest() {
        assertTrue(ontology.containsClass(classIRI));
    }

    @Test
    public void containsClassWhenMissingTest() {
        assertFalse(ontology.containsClass(errorIRI));
    }

    @Test
    public void getAllClassObjectPropertiesTest() throws Exception {
        assertEquals(2, ontology.getAllClassObjectProperties(classIRI).size());
        assertEquals(1, ontology.getAllClassObjectProperties(classIRIC).size());
        assertEquals(1, ontology.getAllClassObjectProperties(classIRID).size());
        assertEquals(1, ontology.getAllClassObjectProperties(classIRIE).size());
    }

    @Test
    public void getAllClassObjectPropertiesWithImportsTest() throws Exception {
        // Setup:
        Resource ont2IRI = vf.createIRI("http://matonto.org/ontology/test-local-imports-2");
        Resource ont2RecordIRI = vf.createIRI("https://matonto.org/record/test-local-imports-2");
        InputStream stream2 = this.getClass().getResourceAsStream("/test-local-imports-2.ttl");
        Ontology ont2 = new SimpleOntology(stream2, ontologyManager, transformer);
        when(ontologyManager.getOntologyRecordResource(ont2IRI)).thenReturn(Optional.of(ont2RecordIRI));
        when(ontologyManager.retrieveOntology(ont2RecordIRI)).thenReturn(Optional.of(ont2));

        InputStream stream1 = this.getClass().getResourceAsStream("/test-local-imports-1.ttl");
        Ontology ont1 = new SimpleOntology(stream1, ontologyManager, transformer);

        assertEquals(1, ont1.getAllClassObjectProperties(importedIRI0).size());
        assertEquals(1, ont1.getAllClassObjectProperties(importedIRI).size());
    }



    @Test(expected = IllegalArgumentException.class)
    public void getAllClassObjectPropertiesWhenMissingTest() throws Exception {
        ontology.getAllClassObjectProperties(errorIRI);
    }

    @Test
    public void getAllNoDomainObjectPropertiesTest() {
        assertEquals(1, ontology.getAllNoDomainObjectProperties().size());
    }

    @Test
    public void getAllClassDataPropertiesTest() throws Exception {
        assertEquals(2, ontology.getAllClassDataProperties(classIRI).size());
        assertEquals(1, ontology.getAllClassDataProperties(classIRIC).size());
        assertEquals(1, ontology.getAllClassDataProperties(classIRID).size());
        assertEquals(1, ontology.getAllClassDataProperties(classIRIE).size());
    }

    @Test
    public void getAllClassDataPropertiesWithImportsTest() throws Exception {
        // Setup:
        Resource ont2IRI = vf.createIRI("http://matonto.org/ontology/test-local-imports-2");
        Resource ont2RecordIRI = vf.createIRI("https://matonto.org/record/test-local-imports-2");
        InputStream stream2 = this.getClass().getResourceAsStream("/test-local-imports-2.ttl");
        Ontology ont2 = new SimpleOntology(stream2, ontologyManager, transformer);
        when(ontologyManager.getOntologyRecordResource(ont2IRI)).thenReturn(Optional.of(ont2RecordIRI));
        when(ontologyManager.retrieveOntology(ont2RecordIRI)).thenReturn(Optional.of(ont2));

        InputStream stream1 = this.getClass().getResourceAsStream("/test-local-imports-1.ttl");
        Ontology ont1 = new SimpleOntology(stream1, ontologyManager, transformer);

        assertEquals(1, ont1.getAllClassDataProperties(importedIRI0).size());
        assertEquals(1, ont1.getAllClassDataProperties(importedIRI).size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void getAllClassDataPropertiesWhenMissingTest() throws Exception {
        ontology.getAllClassDataProperties(errorIRI);
    }

    @Test
    public void getAllNoDomainDataPropertiesTest() {
        assertEquals(1, ontology.getAllNoDomainDataProperties().size());
    }
}
