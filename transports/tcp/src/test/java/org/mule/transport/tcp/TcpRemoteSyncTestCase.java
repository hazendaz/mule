/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.transport.tcp;

import org.mule.DefaultMuleMessage;
import org.mule.api.MuleMessage;
import org.mule.api.config.MuleProperties;
import org.mule.api.endpoint.InboundEndpoint;
import org.mule.module.client.MuleClient;
import org.mule.tck.DynamicPortTestCase;

import java.util.HashMap;
import java.util.Map;

public class TcpRemoteSyncTestCase extends DynamicPortTestCase
{
   
   public static final String message = "mule";
   
   protected String getConfigResources() 
   {
       return "tcp-remotesync.xml";
   }
   
   public void testTcpTcpRemoteSync() throws Exception
   {
       MuleClient client = new MuleClient(muleContext);
       Map<String, Object> props = new HashMap<String, Object>();
       
       //must notify the client to wait for a response from the server
       props.put(MuleProperties.MULE_REMOTE_SYNC_PROPERTY, Boolean.TRUE);
       MuleMessage reply = client.send(((InboundEndpoint) client.getMuleContext().getRegistry().lookupObject("echoInTcp")).getAddress(),
           new DefaultMuleMessage(message, muleContext), props);

       assertNotNull(reply);
       assertNotNull(reply.getPayload());
       assertEquals("Received: " + message, reply.getPayloadAsString());
   }
    
    public void testTcpVmRemoteSync() throws Exception
    {
        MuleClient client = new MuleClient(muleContext);
        Map<String, Object> props = new HashMap<String, Object>();
        
        //must notify the client to wait for a response from the server
        props.put(MuleProperties.MULE_REMOTE_SYNC_PROPERTY, Boolean.TRUE);
        
        MuleMessage reply = client.send(((InboundEndpoint) client.getMuleContext().getRegistry().lookupObject("echo2InTcp")).getAddress(), 
            new DefaultMuleMessage(message, muleContext), props);

        assertNotNull(reply);
        assertNotNull(reply.getPayload());
        assertEquals("Received: " + message, reply.getPayloadAsString());
    }

    @Override
    protected int getNumPortsToFind()
    {
        return 3;
    }
}
