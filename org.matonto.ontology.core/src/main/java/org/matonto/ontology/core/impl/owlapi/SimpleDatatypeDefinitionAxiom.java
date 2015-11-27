package org.matonto.ontology.core.impl.owlapi;

import java.util.Set;

import org.matonto.ontology.core.api.Annotation;
import org.matonto.ontology.core.api.DataRange;
import org.matonto.ontology.core.api.Datatype;
import org.matonto.ontology.core.api.DatatypeDefinitionAxiom;

import com.google.common.base.Preconditions;


public class SimpleDatatypeDefinitionAxiom 
	extends SimpleAxiom 
	implements DatatypeDefinitionAxiom {


	private Datatype datatype;
	private DataRange dataRange;
	
	
	public SimpleDatatypeDefinitionAxiom(Datatype datatype, DataRange dataRange, Set<Annotation> annotations) 
	{
		super(annotations);
		this.datatype = Preconditions.checkNotNull(datatype, "datatype cannot be null");
		this.dataRange = Preconditions.checkNotNull(dataRange, "dataRange cannot be null");
	}

	
	@Override
	public DatatypeDefinitionAxiom getAxiomWithoutAnnotations() 
	{
		if(!isAnnotated())
			return this;
		
		return new SimpleDatatypeDefinitionAxiom(datatype, dataRange, NO_ANNOTATIONS);	
	}

	
	@Override
	public DatatypeDefinitionAxiom getAnnotatedAxiom(Set<Annotation> annotations) 
	{
		return new SimpleDatatypeDefinitionAxiom(datatype, dataRange, mergeAnnos(annotations));
	}

	
	@Override
	public SimpleAxiomType getAxiomType() 
	{
		return SimpleAxiomType.DATATYPE_DEFINITION;
	}

	
	@Override
	public Datatype getDatatype() 
	{
		return datatype;
	}

	
	@Override
	public DataRange getDataRange() 
	{
		return dataRange;
	}
	
	
	@Override
	public boolean equals(Object obj)
	{
		if (this == obj) 
		    return true;
		
		if (!super.equals(obj)) 
			return false;
		
		if (obj instanceof DatatypeDefinitionAxiom) {
			DatatypeDefinitionAxiom other = (DatatypeDefinitionAxiom)obj;			 
			return ((datatype.equals(other.getDatatype())) && (dataRange.equals(other.getDataRange())));
		}
		
		return false;
	}

}
