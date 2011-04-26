/**
* Licensed to the Apache Software Foundation (ASF) under one
* or more contributor license agreements.  See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership.  The ASF licenses this file
* to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
* with the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package org.apache.hadoop.yarn.event;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.yarn.service.AbstractService;

/**
 * Dispatches events in a separate thread. Currently only single thread does
 * that. Potentially there could be multiple channels for each event type
 * class and a thread pool can be used to dispatch the events.
 *
 */
public class AsyncDispatcher extends AbstractService implements Dispatcher {

  private static final Log LOG = LogFactory.getLog(AsyncDispatcher.class);

  private BlockingQueue<Event> eventQueue = new LinkedBlockingQueue<Event>();
  private volatile boolean stopped = false;
  
  private Thread eventHandlingThread;
  private Map<Class<? extends Enum>, EventHandler> eventDispatchers = 
    new HashMap<Class<? extends Enum>, EventHandler>();

  public AsyncDispatcher() {
    super("Dispatcher");
  }

  @Override
  public void start() {
    //start all the components
    super.start();

    eventHandlingThread = new Thread(new Runnable() {
      @Override
      public void run() {
        while (!stopped && !Thread.currentThread().isInterrupted()) {
          Event event;
          try {
            event = eventQueue.take();
          } catch(InterruptedException ie) {
            LOG.info("AsyncDispatcher thread interrupted", ie);
            return;
          }
          if (event != null) {
            dispatch(event);
          }
        }
      }
    });
    eventHandlingThread.start();
  }

  @Override
  public void stop() {
    stopped = true;
    eventHandlingThread.interrupt();
    try {
      eventHandlingThread.join();
    } catch (InterruptedException ie) {
      LOG.debug("Interruped Exception while stopping", ie);
    }

    //stop all the components
    super.stop();
  }

  protected void dispatch(Event event) {
    //all events go thru this loop
    LOG.info("Dispatching the event " + event.toString());

    Class<? extends Enum> type = event.getType().getDeclaringClass();
    
    try{
      eventDispatchers.get(type).handle(event);
    }
    catch (Throwable t) {
      LOG.fatal("Error in dispatcher thread. Exiting..", t);
      System.exit(-1);
    }
  }

  @Override
  public void register(Class<? extends Enum> eventType, EventHandler handler) {
    eventDispatchers.put(eventType, handler);
  }

  @Override
  public EventHandler getEventHandler() {
    // TODO Auto-generated method stub
    return new EventHandler() {
      @Override
      public void handle(Event event) {
        /* all this method does is enqueue all the events onto the queue */
        eventQueue.offer(event);
      }
      
    };
  }

}