/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2013, Red Hat Inc. or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat Inc.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.hibernate.metamodel.internal;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.logging.Logger;

import org.hibernate.AssertionFailure;
import org.hibernate.internal.CoreMessageLogger;
import org.hibernate.internal.util.StringHelper;
import org.hibernate.metamodel.spi.source.AggregatedCompositeIdentifierSource;
import org.hibernate.metamodel.spi.source.AttributeSource;
import org.hibernate.metamodel.spi.source.ComponentAttributeSource;
import org.hibernate.metamodel.spi.source.EntitySource;
import org.hibernate.metamodel.spi.source.IdentifierSource;
import org.hibernate.metamodel.spi.source.NonAggregatedCompositeIdentifierSource;
import org.hibernate.metamodel.spi.source.PluralAttributeSource;
import org.hibernate.metamodel.spi.source.RootEntitySource;
import org.hibernate.metamodel.spi.source.SimpleIdentifierSource;
import org.hibernate.metamodel.spi.source.SingularAttributeSource;

/**
 * @author Gail Badner
 */
public class SourceIndex {
	private static final CoreMessageLogger log = Logger.getMessageLogger(
			CoreMessageLogger.class,
			SourceIndex.class.getName()
	);
	private static final String EMPTY_STRING = "";

	private final Map<String, EntitySourceIndex> entitySourceIndexByEntityName = new HashMap<String, EntitySourceIndex>();
	private final Map<AttributeSourceKey, AttributeSource> attributeSourcesByKey = new HashMap<AttributeSourceKey, AttributeSource>();

	public void indexEntitySource(final EntitySource entitySource) {
		String entityName = entitySource.getEntityName();
		EntitySourceIndex entitySourceIndex = new EntitySourceIndex( entitySource );
		entitySourceIndexByEntityName.put( entityName, entitySourceIndex );
		log.debugf( "Mapped entity source \"%s\"", entityName );
		indexAttributes( entitySourceIndex );
	}

	public Map<AttributeSourceKey, SingularAttributeSource> getSingularAttributeSources(
			String entityName,
			SingularAttributeSource.Nature nature) {
		final EntitySourceIndex entitySourceIndex = entitySourceIndexByEntityName.get( entityName );
		return entitySourceIndex.getSingularAttributeSources( nature );
	}

	public AttributeSource attributeSource(final String entityName, final String attributePath) {
		return attributeSourcesByKey.get( new AttributeSourceKey( entityName, attributePath ) );
	}

	public EntitySource entitySource(final String entityName) {
		return entitySourceIndexByEntityName.get( entityName ).entitySource;
	}

	private void indexAttributes(EntitySourceIndex entitySourceIndex) {
		final String emptyString = "";
		if ( entitySourceIndex.entitySource instanceof RootEntitySource ) {
			indexIdentifierAttributeSources( entitySourceIndex );
		}
		for ( final AttributeSource attributeSource : entitySourceIndex.entitySource.attributeSources() ) {
			indexAttributeSources(entitySourceIndex, emptyString, attributeSource, false );
		}
	}

	private void indexIdentifierAttributeSources(EntitySourceIndex entitySourceIndex)  {
		RootEntitySource rootEntitySource = (RootEntitySource) entitySourceIndex.entitySource;
		IdentifierSource identifierSource = rootEntitySource.getIdentifierSource();
		switch ( identifierSource.getNature() ) {
			case SIMPLE:
				final AttributeSource identifierAttributeSource =
						( (SimpleIdentifierSource) identifierSource ).getIdentifierAttributeSource();
				indexAttributeSources( entitySourceIndex, EMPTY_STRING, identifierAttributeSource, true );
				break;
			case NON_AGGREGATED_COMPOSITE:
				final List<SingularAttributeSource> nonAggregatedAttributeSources =
						( (NonAggregatedCompositeIdentifierSource) identifierSource ).getAttributeSourcesMakingUpIdentifier();
				for ( SingularAttributeSource nonAggregatedAttributeSource : nonAggregatedAttributeSources ) {
					indexAttributeSources( entitySourceIndex, EMPTY_STRING, nonAggregatedAttributeSource, true );
				}
				break;
			case AGGREGATED_COMPOSITE:
				final ComponentAttributeSource aggregatedAttributeSource =
						( (AggregatedCompositeIdentifierSource) identifierSource ).getIdentifierAttributeSource();
				indexAttributeSources( entitySourceIndex, EMPTY_STRING, aggregatedAttributeSource, true );
				break;
			default:
				throw new AssertionFailure(
						String.format( "Unknown type of identifier: [%s]", identifierSource.getNature() )
				);
		}
	}

	private void indexAttributeSources(
			EntitySourceIndex entitySourceIndex,
			String pathBase,
			AttributeSource attributeSource,
			boolean isInIdentifier) {
		AttributeSourceKey key = new AttributeSourceKey( entitySourceIndex.entitySource.getEntityName(), pathBase, attributeSource.getName() );
		attributeSourcesByKey.put( key, attributeSource );
		log.debugf(
				"Mapped attribute source \"%s\"", key
		);
		if ( attributeSource.isSingular() ) {
			entitySourceIndex.indexSingularAttributeSource( pathBase, (SingularAttributeSource) attributeSource, isInIdentifier );
		}
		else {
			entitySourceIndex.indexPluralAttributeSource( pathBase, (PluralAttributeSource) attributeSource );
		}
		if ( attributeSource instanceof ComponentAttributeSource ) {
			for ( AttributeSource subAttributeSource : ( (ComponentAttributeSource) attributeSource ).attributeSources() ) {
				indexAttributeSources(
						entitySourceIndex,
						key.attributePath(),
						subAttributeSource,
						isInIdentifier
				);
			}
		}
	}

	public static class AttributeSourceKey {

		private final String entityName;
		private final String containerPath;
		private final String attributeName;

		private AttributeSourceKey(final String entityName, final String containerPath, final String attributeName) {
			this.entityName = entityName;
			this.containerPath = containerPath;
			this.attributeName = attributeName;
		}

		private AttributeSourceKey(final String entityName, final String attributePath) {
			this.entityName = entityName;
			int indexLastDot = attributePath.lastIndexOf( '.' );
			if ( indexLastDot == -1 ) {
				this.containerPath = EMPTY_STRING;
				this.attributeName = attributePath;
			}
			else {
				this.containerPath = attributePath.substring( 0, indexLastDot );
				this.attributeName = attributePath.substring( indexLastDot + 1 );
			}
		}

		public String entityName() {
			return entityName;
		}

		public String containerPath() {
			return containerPath;
		}

		public String attributeName() {
			return attributeName;
		}

		public String attributePath() {
			return StringHelper.isEmpty( containerPath ) ?
					attributeName :
					containerPath + '.' + attributeName;
		}

		@Override
		public String toString() {
			return entityName + '.' + attributePath();
		}

		@Override
		public boolean equals(Object o) {
			if ( this == o ) {
				return true;
			}
			if ( o == null || getClass() != o.getClass() ) {
				return false;
			}

			AttributeSourceKey that = (AttributeSourceKey) o;

			if ( !attributeName.equals( that.attributeName ) ) {
				return false;
			}
			if ( !containerPath.equals( that.containerPath ) ) {
				return false;
			}
			if ( !entityName.equals( that.entityName ) ) {
				return false;
			}

			return true;
		}

		@Override
		public int hashCode() {
			int result = entityName.hashCode();
			result = 31 * result + containerPath.hashCode();
			result = 31 * result + attributeName.hashCode();
			return result;
		}
	}

	private static class EntitySourceIndex {
		private final EntitySource entitySource;
		private final Map<SingularAttributeSource.Nature, Map<AttributeSourceKey, SingularAttributeSource>> identifierAttributeSourcesByNature =
				new HashMap<SingularAttributeSource.Nature, Map<AttributeSourceKey, SingularAttributeSource>>();
		private final Map<SingularAttributeSource.Nature, Map<AttributeSourceKey, SingularAttributeSource>> singularAttributeSourcesByNature =
				new HashMap<SingularAttributeSource.Nature, Map<AttributeSourceKey, SingularAttributeSource>>();
		private final Map<PluralAttributeSource.Nature, Set<PluralAttributeSource>> pluralAttributeSourcesByNature =
				new HashMap<PluralAttributeSource.Nature, Set<PluralAttributeSource>>();

		private EntitySourceIndex(final EntitySource entitySource) {
			this.entitySource = entitySource;
		}

		private Map<AttributeSourceKey, SingularAttributeSource> getSingularAttributeSources(
				SingularAttributeSource.Nature nature) {
			final Map<AttributeSourceKey, SingularAttributeSource> entries;
			if ( singularAttributeSourcesByNature.containsKey( nature ) ) {
				entries = Collections.unmodifiableMap( singularAttributeSourcesByNature.get( nature ) );
			}
			else {
				entries = Collections.emptyMap();
			}
			return entries;
		}

		private void indexSingularAttributeSource(
				String pathBase,
				SingularAttributeSource attributeSource,
				boolean isInIdentifier) {
			indexSingularAttributeSource(
					entitySource.getEntityName(),
					pathBase,
					attributeSource,
					isInIdentifier ? identifierAttributeSourcesByNature : singularAttributeSourcesByNature );
		}

		private static void indexSingularAttributeSource(
				String entityName,
				String pathBase,
				SingularAttributeSource attributeSource,
				Map<SingularAttributeSource.Nature, Map<AttributeSourceKey, SingularAttributeSource>> map) {
			final Map<AttributeSourceKey, SingularAttributeSource> singularAttributeSources;
			if ( map.containsKey( attributeSource.getNature() ) ) {
				singularAttributeSources = map.get( attributeSource.getNature() );
			}
			else {
				singularAttributeSources = new LinkedHashMap<AttributeSourceKey,SingularAttributeSource>();
				map.put( attributeSource.getNature(), singularAttributeSources );
			}
			AttributeSourceKey key = new AttributeSourceKey( entityName, pathBase, attributeSource.getName() );
			if ( singularAttributeSources.put( key, attributeSource ) != null ) {
				throw new AssertionFailure(
						String.format( "Attempt to reindex attribute source for: [%s]",  key )
				);
			}
		}

		private void indexPluralAttributeSource(String pathBase, PluralAttributeSource attributeSource) {
			final Set<PluralAttributeSource> pluralAttributeSources;
			if ( pluralAttributeSourcesByNature.containsKey( attributeSource.getNature() ) ) {
				pluralAttributeSources = pluralAttributeSourcesByNature.get( attributeSource.getNature() );
			}
			else {
				pluralAttributeSources = new LinkedHashSet<PluralAttributeSource>();
				pluralAttributeSourcesByNature.put( attributeSource.getNature(), pluralAttributeSources );
			}
			if ( !pluralAttributeSources.add( attributeSource ) ) {
				throw new AssertionFailure(
						String.format(
								"Attempt to reindex attribute source for: [%s]",
								new AttributeSourceKey( entitySource.getEntityName(), pathBase, attributeSource.getName() )
						)
				);
			}
		}
	}
}
