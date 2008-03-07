/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.scr.impl;


import java.util.*;

import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.service.component.*;
import org.osgi.service.log.LogService;


/**
 * The <code>ComponentFactoryImpl</code> TODO
 *
 * @author fmeschbe
 */
public class ComponentFactoryImpl extends AbstractComponentManager implements ComponentFactory, ManagedServiceFactory
{

    // The component registry used to retrieve component IDs
    private ComponentRegistry m_componentRegistry;

    // The map of components created from Configuration objects
    // maps PID to ComponentManager for configuration updating
    // this map is lazily created
    private Map m_configuredServices;

    // Actually we only use the identity key stuff, but there is
    // no IdentityHashSet and HashSet internally uses a HashMap anyway
    private Map m_createdComponents;


    ComponentFactoryImpl( BundleComponentActivator activator, ComponentMetadata metadata,
        ComponentRegistry componentRegistry )
    {
        super( activator, metadata, componentRegistry.createComponentId() );
        m_componentRegistry = componentRegistry;
        m_createdComponents = new IdentityHashMap();
    }


    /* (non-Javadoc)
     * @see org.osgi.service.component.ComponentFactory#newInstance(java.util.Dictionary)
     */
    public ComponentInstance newInstance( Dictionary dictionary )
    {
        return ( ComponentInstance ) createComponentManager( dictionary );
    }


    protected boolean createComponent()
    {
        // not component to create, newInstance must be used instead
        return true;
    }


    protected void deleteComponent()
    {
        // nothing to delete
    }


    protected ServiceRegistration registerComponentService()
    {
        getActivator().log( LogService.LOG_DEBUG, "registering component factory", getComponentMetadata(), null );

        Dictionary serviceProperties = getProperties();
        return getActivator().getBundleContext().registerService( new String[]
            { ComponentFactory.class.getName(), ManagedServiceFactory.class.getName() }, getService(),
            serviceProperties );
    }


    public Object getInstance()
    {
        // this does not return the component instance actually
        return null;
    }


    public Dictionary getProperties()
    {
        Dictionary props = new Hashtable();

        // 112.5.5 The Component Factory service must register with the following properties
        props.put( ComponentConstants.COMPONENT_NAME, getComponentMetadata().getName() );
        props.put( ComponentConstants.COMPONENT_FACTORY, getComponentMetadata().getFactoryIdentifier() );

        // also register with the factory PID
        props.put( Constants.SERVICE_PID, getComponentMetadata().getName() );

        // descriptive service properties
        props.put( Constants.SERVICE_DESCRIPTION, "ManagedServiceFactory for Factory Component"
            + getComponentMetadata().getName() );
        props.put( Constants.SERVICE_VENDOR, "Apache Software Foundation" );

        return props;
    }


    protected Object getService()
    {
        return this;
    }


    //---------- ManagedServiceFactory interface ------------------------------


    public void updated( String pid, Dictionary configuration )
    {
        ComponentManager cm;
        if ( m_configuredServices != null )
        {
            cm = ( ComponentManager ) m_configuredServices.get( pid );
        }
        else
        {
            m_configuredServices = new HashMap();
            cm = null;
        }

        if ( cm == null )
        {
            // create a new instance with the current configuration
            cm = createComponentManager( configuration );

            // keep a reference for future updates
            m_configuredServices.put( pid, cm );
        }
        else if ( cm instanceof ImmediateComponentManager )
        {
            // update the configuration as if called as ManagedService
            ( ( ImmediateComponentManager ) cm ).reconfigure( configuration );
        }
    }


    public void deleted( String pid )
    {
        if ( m_configuredServices != null )
        {
            ComponentManager cm = ( ComponentManager ) m_configuredServices.remove( pid );
            if ( cm != null )
            {
                getActivator().log( LogService.LOG_DEBUG, "Disposing component after configuration deletion",
                    getComponentMetadata(), null );

                disposeComponentManager( cm );
            }
        }

    }


    public String getName()
    {
        return "Component Factory " + getComponentMetadata().getName();
    }


    //---------- internal -----------------------------------------------------

    /**
     * ComponentManager instances created by this method are not registered
     * with the ComponentRegistry. Therefore, any configuration update to these
     * components must be effected by this class !
     */
    private ComponentManager createComponentManager( Dictionary configuration )
    {
        long componentId = m_componentRegistry.createComponentId();
        ComponentManager cm = ManagerFactory.createManager( getActivator(), getComponentMetadata(),
            componentId );

        // add the new component to the activators instances
        getActivator().getInstanceReferences().add( cm );

        // register with the internal set of created components
        m_createdComponents.put( cm, cm );

        // inject configuration if possible
        if ( cm instanceof ImmediateComponentManager )
        {
            ( ( ImmediateComponentManager ) cm ).setFactoryProperties( configuration );
        }

        // immediately enable this ComponentManager
        cm.enable();

        return cm;
    }


    private void disposeComponentManager( ComponentManager cm )
    {
        // remove from created components
        m_createdComponents.remove( cm );

        // remove from activators list
        getActivator().getInstanceReferences().remove( cm );

        // finally dispose it
        cm.dispose();
    }
}
