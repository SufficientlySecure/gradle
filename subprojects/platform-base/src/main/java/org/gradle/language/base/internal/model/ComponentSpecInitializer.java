/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.language.base.internal.model;

import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.NamedDomainObjectCollection;
import org.gradle.api.Transformer;
import org.gradle.internal.*;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.model.internal.core.ModelCreator;
import org.gradle.model.internal.core.ModelCreators;
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.core.MutableModelNode;
import org.gradle.model.internal.core.rule.describe.StandardDescriptorFactory;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.BinaryTasksCollection;
import org.gradle.platform.base.ComponentSpec;
import org.gradle.platform.base.internal.BinarySpecInternal;
import org.gradle.platform.base.internal.ComponentSpecInternal;

import static org.gradle.internal.Cast.uncheckedCast;

public class ComponentSpecInitializer {

    private final static BiAction<MutableModelNode, ComponentSpec> ACTION = createAction();

    public static BiAction<MutableModelNode, ComponentSpec> action() {
        return ACTION;
    }

    public static BiAction<MutableModelNode, BinarySpec> binaryAction() {
        return new BiAction<MutableModelNode, BinarySpec>() {
            @Override
            public void execute(MutableModelNode node, BinarySpec spec) {
                ModelType<BinaryTasksCollection> itemType = ModelType.of(BinaryTasksCollection.class);
                ModelReference<BinaryTasksCollection> itemReference = ModelReference.of(node.getPath().child("tasks"), itemType);
                ModelCreator itemCreator = ModelCreators.unmanagedInstance(itemReference, Factories.constant(spec.getTasks()))
                    .descriptor(new StandardDescriptorFactory(node.getDescriptor()).transform("tasks"))
                    .build();
                node.addLink(itemCreator);
            }
        };
    }

    private static BiAction<MutableModelNode, ComponentSpec> createAction() {
        Transformer<NamedDomainObjectCollection<LanguageSourceSet>, ComponentSpecInternal> sourcesPropertyTransformer = new Transformer<NamedDomainObjectCollection<LanguageSourceSet>, ComponentSpecInternal>() {
            public NamedDomainObjectCollection<LanguageSourceSet> transform(ComponentSpecInternal componentSpec) {
                return componentSpec.getSources();
            }
        };
        BiAction<MutableModelNode, ComponentSpec> sourcePropertyRegistrar = domainObjectCollectionModelRegistrar("sources", namedDomainObjectCollectionOf(LanguageSourceSet.class),
                sourcesPropertyTransformer);

        Transformer<NamedDomainObjectCollection<BinarySpec>, ComponentSpecInternal> binariesPropertyTransformer = new Transformer<NamedDomainObjectCollection<BinarySpec>, ComponentSpecInternal>() {
            public NamedDomainObjectCollection<BinarySpec> transform(ComponentSpecInternal componentSpec) {
                return componentSpec.getBinaries();
            }
        };
        ModelType<NamedDomainObjectCollection<BinarySpec>> binariesType = namedDomainObjectCollectionOf(BinarySpec.class);
        BiAction<BinarySpec, ComponentSpecInternal> binaryInitializationAction = new BiAction<BinarySpec, ComponentSpecInternal>() {
            public void execute(BinarySpec binary, ComponentSpecInternal component) {
                BinarySpecInternal binaryInternal = uncheckedCast(binary);
                binaryInternal.setBinarySources(component.getSources().copy(binary.getName()));
            }
        };
        BiAction<MutableModelNode, ComponentSpec> binariesPropertyRegistrar = domainObjectCollectionModelRegistrar("binaries", binariesType, binariesPropertyTransformer, binaryInitializationAction,
            Actions.doNothing());
        @SuppressWarnings("unchecked")
        BiAction<MutableModelNode, ComponentSpec> initializer = BiActions.composite(sourcePropertyRegistrar, binariesPropertyRegistrar);
        return initializer;
    }

    private static <T> ModelType<NamedDomainObjectCollection<T>> namedDomainObjectCollectionOf(Class<T> type) {
        return new ModelType.Builder<NamedDomainObjectCollection<T>>() {
        }.where(new ModelType.Parameter<T>() {
        }, ModelType.of(type)).build();
    }

    private static <T extends Named, C extends NamedDomainObjectCollection<T>> BiAction<MutableModelNode, ComponentSpec> domainObjectCollectionModelRegistrar(
        final String domainObjectCollectionName, final ModelType<C> collectionType, final Transformer<C, ComponentSpecInternal> collectionTransformer
    ) {
        return domainObjectCollectionModelRegistrar(domainObjectCollectionName, collectionType, collectionTransformer, BiActions.doNothing(), Actions.doNothing());
    }

    private static <T extends Named, C extends NamedDomainObjectCollection<T>> BiAction<MutableModelNode, ComponentSpec> domainObjectCollectionModelRegistrar(
        final String domainObjectCollectionName, final ModelType<C> collectionType, final Transformer<C, ComponentSpecInternal> collectionTransformer,
        final BiAction<? super T, ? super ComponentSpecInternal> itemInitializationAction, Action<? super MutableModelNode> itemNodeInitializationAction
    ) {
        return new DomainObjectCollectionModelRegistrationAction<T, C>(domainObjectCollectionName, collectionType, collectionTransformer, itemInitializationAction, itemNodeInitializationAction);
    }

    private static <T extends Named, C extends NamedDomainObjectCollection<T>> Action<MutableModelNode> domainObjectCollectionItemModelRegistrar(
        final ModelType<C> collectionType, final StandardDescriptorFactory itemCreatorDescriptorFactory, final BiAction<? super T, ? super ComponentSpecInternal> itemInitializationAction,
        Action<? super MutableModelNode> itemNodeInitializationAction) {
        return new DomainObjectCollectionItemModelRegistrationAction<T, C>(collectionType, itemInitializationAction, itemNodeInitializationAction, itemCreatorDescriptorFactory);
    }

    private static class DomainObjectCollectionItemModelRegistrationAction<T extends Named, C extends NamedDomainObjectCollection<T>> implements Action<MutableModelNode> {
        private final ModelType<C> collectionType;
        private final BiAction<? super T, ? super ComponentSpecInternal> itemInitializationAction;
        private final StandardDescriptorFactory itemCreatorDescriptorFactory;
        private final Action<? super MutableModelNode> itemNodeInitializationAction;

        public DomainObjectCollectionItemModelRegistrationAction(ModelType<C> collectionType, BiAction<? super T, ? super ComponentSpecInternal> itemInitializationAction, Action<? super MutableModelNode> itemNodeInitializationAction, StandardDescriptorFactory itemCreatorDescriptorFactory) {
            this.collectionType = collectionType;
            this.itemInitializationAction = itemInitializationAction;
            this.itemNodeInitializationAction = itemNodeInitializationAction;
            this.itemCreatorDescriptorFactory = itemCreatorDescriptorFactory;
        }

        @Override
        public void execute(final MutableModelNode collectionModelNode) {
            collectionModelNode.getPrivateData(collectionType).all(new Action<T>() {
                @Override
                public void execute(T item) {
                    ComponentSpec componentSpec = collectionModelNode.getParent().getPrivateData(ModelType.of(ComponentSpec.class));
                    itemInitializationAction.execute(item, (ComponentSpecInternal) componentSpec);
                    final String name = item.getName();
                    ModelType<T> itemType = ModelType.typeOf(item);
                    ModelReference<T> itemReference = ModelReference.of(collectionModelNode.getPath().child(name), itemType);
                    ModelCreator itemCreator = ModelCreators.unmanagedInstance(itemReference, new Factory<T>() {
                        public T create() {
                            return collectionModelNode.getPrivateData(collectionType).getByName(name);
                        }
                    }, itemNodeInitializationAction)
                        .descriptor(itemCreatorDescriptorFactory.transform(name))
                        .build();

                    collectionModelNode.addLink(itemCreator);
                    collectionModelNode.getLink(name).ensureUsable();
                }
            });
        }
    }

    private static class DomainObjectCollectionModelRegistrationAction<T extends Named, C extends NamedDomainObjectCollection<T>> implements BiAction<MutableModelNode, ComponentSpec> {

        private final String domainObjectCollectionName;
        private final ModelType<C> collectionType;
        private final Transformer<C, ComponentSpecInternal> collectionTransformer;
        private final BiAction<? super T, ? super ComponentSpecInternal> itemInitializationAction;
        private final Action<? super MutableModelNode> itemNodeInitializationAction;

        public DomainObjectCollectionModelRegistrationAction(String domainObjectCollectionName, ModelType<C> collectionType, Transformer<C, ComponentSpecInternal> collectionTransformer,
                                                             BiAction<? super T, ? super ComponentSpecInternal> itemInitializationAction,
                                                             Action<? super MutableModelNode> itemNodeInitializationAction) {
            this.domainObjectCollectionName = domainObjectCollectionName;
            this.collectionType = collectionType;
            this.collectionTransformer = collectionTransformer;
            this.itemInitializationAction = itemInitializationAction;
            this.itemNodeInitializationAction = itemNodeInitializationAction;
        }

        @Override
        public void execute(final MutableModelNode mutableModelNode, final ComponentSpec componentSpec) {
            ModelReference<C> reference = ModelReference.of(mutableModelNode.getPath().child(domainObjectCollectionName), collectionType);
            String containerCreatorDescriptor = new StandardDescriptorFactory(mutableModelNode.getDescriptor()).transform(domainObjectCollectionName);

            final StandardDescriptorFactory itemCreatorDescriptorFactory = new StandardDescriptorFactory(containerCreatorDescriptor);

            Factory<C> domainObjectCollectionFactory = new Factory<C>() {
                public C create() {
                    ComponentSpec componentSpec = mutableModelNode.getPrivateData(ModelType.of(ComponentSpec.class));
                    return collectionTransformer.transform((ComponentSpecInternal) componentSpec);
                }
            };
            Action<MutableModelNode> itemRegistrar = domainObjectCollectionItemModelRegistrar(collectionType, itemCreatorDescriptorFactory, itemInitializationAction, itemNodeInitializationAction);
            mutableModelNode.addLink(
                    ModelCreators.unmanagedInstance(reference, domainObjectCollectionFactory, itemRegistrar)
                            .descriptor(containerCreatorDescriptor)
                            .build()
            );
            mutableModelNode.getLink(domainObjectCollectionName).ensureUsable();
        }
    }
}
