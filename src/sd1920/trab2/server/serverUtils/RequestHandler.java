package sd1920.trab2.server.serverUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.ws.Service;
import javax.xml.ws.WebServiceException;

import org.glassfish.jersey.client.ClientConfig;

import javax.xml.ws.BindingProvider;
import com.sun.xml.ws.client.BindingProviderProperties;

import sd1920.trab2.api.Message;
import sd1920.trab2.api.Discovery.DomainInfo;
import sd1920.trab2.api.rest.MessageServiceRest;
import sd1920.trab2.api.soap.MessageServiceSoap;
import sd1920.trab2.api.soap.MessagesException;
import sd1920.trab2.server.proxy.ProxyMailServer;
import sd1920.trab2.server.proxy.requests.Copy;
import sd1920.trab2.server.replica.ReplicaMailServerREST;
import sd1920.trab2.server.rest.RESTMailServer;
import sd1920.trab2.server.rest.resources.MessageResourceRest;
import sd1920.trab2.server.soap.SOAPMailServer;

/**
 * Used for asynchronously launching requests to other servers
 */
public class RequestHandler implements Runnable {

    private static final int SLEEP_TIME = 1000;

    protected static Client client;
    private ServerUtils utils;
    private static Logger Log = LocalServerUtils.Log;

    private BlockingQueue<Request> requests;

    public RequestHandler(ClientConfig config, ServerUtils utils) {
        client = ClientBuilder.newClient(config);

        this.utils = utils;

        this.requests = new LinkedBlockingQueue<>();
    }

    public void addRequest(Request request) {
        synchronized(this.requests){
            this.requests.add(request);
        }
    }

    private static DomainInfo getRequestTarget(Request request){
        switch (request.getType()) {
            case REST:
                return RESTMailServer.serverRecord.knownUrisOf(request.getDomain());
            case REST_REPLICA:
                return ReplicaMailServerREST.serverRecord.knownUrisOf(request.getDomain());
            case SOAP:
                return SOAPMailServer.serverRecord.knownUrisOf(request.getDomain());
            case PROXY:
                return ProxyMailServer.serverRecord.knownUrisOf(request.getDomain());
            default:
                return null;
        }
    }

    /**
     * Given a postRequest, forwards it to the target domain
     * No logs here because it would be total chaos given that each domain has a thread 
     * for every other domain
     */
    public static List<String> processPostRequest(PostRequest request) throws ProcessingException, MalformedURLException, WebServiceException,
                                                                             MessagesException{
        Response r = null;
        List<String> failedDeliveries = null;
        DomainInfo uri = getRequestTarget(request);
                                                            
        Message msg = request.getMessage();                                                               
        
        if (uri.isRest()) {            
            WebTarget target = client.target(uri.getUri());
            target = target.path(MessageServiceRest.PATH).path("mbox");
            target = target.queryParam("secret", request.getSecret());
            
            r = target.request().accept(MediaType.APPLICATION_JSON)
                .post(Entity.entity(msg, MediaType.APPLICATION_JSON));

            failedDeliveries = r.readEntity(new GenericType<List<String>>() {});
        } else {
            MessageServiceSoap msgService = null;

            Service service = Service.create(new URL(uri.getUri() + LocalServerUtils.MESSAGES_WSDL),
                    LocalServerUtils.MESSAGE_QNAME);

            msgService = service.getPort(MessageServiceSoap.class);
            
            ((BindingProvider) msgService).getRequestContext().put(BindingProviderProperties.CONNECT_TIMEOUT,
                    LocalServerUtils.TIMEOUT);
            ((BindingProvider) msgService).getRequestContext().put(BindingProviderProperties.REQUEST_TIMEOUT,
                    LocalServerUtils.TIMEOUT);
              
            failedDeliveries = msgService.postForwardedMessage(msg, request.getSecret());
        }
        return failedDeliveries;
    }

    /**
     * Wrapper for processPostRequest. Catches the errors
     * @param request postRequest to execute
     * @return was it successful?
     */
    public boolean execPostRequest(PostRequest request) {
       
        List<String> failedDeliveries = null;
        try{
            failedDeliveries = processPostRequest(request);
        }
        catch (ProcessingException e) {
            Log.info("execPostRequest: Failed to forward message to " + request.getDomain() + ". Retrying...");
            return false;
        }
        catch (MalformedURLException e) {
            Log.info("execPostRequest: Bad Url");
            return false;
        } 
        catch (WebServiceException| MessagesException e) {
            Log.info("execPostRequest: Failed to forward message to " + request.getDomain() + ".");
            return false;
        }

        String senderName = LocalServerUtils.getSenderCanonicalName(request.getMessage().getSender());

        for (String recipient : failedDeliveries) {
            utils.saveErrorMessages(senderName, recipient, request.getMessage());
        }

        return true;
    }

    /**
     * Given a deleteRequest, forwards it to the target domain
     * No logs here because it would be total chaos given that each domain has a thread 
     * for every other domain
     */
    public static void processDeleteRequest(DeleteRequest request) throws ProcessingException, 
                        MessagesException, WebServiceException, MalformedURLException {
    
        DomainInfo uri = getRequestTarget(request);
        long mid = request.getMid();

        if (uri.isRest()) {
            WebTarget target = client.target(uri.getUri());
            target = target.path(MessageResourceRest.PATH).path("msg").path(Long.toString(mid));
            target  = target.queryParam("secret", request.getSecret());

            target.request().delete();
        } else {
            MessageServiceSoap msgService = null;
            
            Service service = Service.create(new URL(uri.getUri() + LocalServerUtils.MESSAGES_WSDL),
                    LocalServerUtils.MESSAGE_QNAME);
            msgService = service.getPort(MessageServiceSoap.class);
            
            ((BindingProvider) msgService).getRequestContext().put(BindingProviderProperties.CONNECT_TIMEOUT,
            LocalServerUtils.TIMEOUT);
            ((BindingProvider) msgService).getRequestContext().put(BindingProviderProperties.REQUEST_TIMEOUT,
            LocalServerUtils.TIMEOUT);
            
            msgService.deleteForwardedMessage(mid, request.getSecret());
        }
    }
    
    /**
     * Wrapper for procesDeleteRequest. Catches the errors
     * @param request deleteRequest to execute
     * @return was it successful?
     */
    public boolean execDeleteRequest(DeleteRequest request) {
        try {
            processDeleteRequest(request);
        } catch (ProcessingException e) {
            Log.info("execDeleteRequest: Failed to redirect request to " + request.getDomain() + ". Retrying...");
            return false;
        } catch (MalformedURLException e) {
            Log.info("execDeleteRequest: Bad Url");
            return false;
        } catch (WebServiceException e) {
            Log.info("execDeleteRequest: Failed to forward message to " + request.getDomain() + ".");
            return false;
        } catch (MessagesException me) {
            Log.info("execDeleteRequest: Error, could not send the message.");
            return true;
        }

        return true;
    }

    private void processCopyRequest(CopyRequest request){
        if(!Copy.run(request.getCopy()) && !request.isForwarded())
            this.utils.saveErrorMessages(request.getSender(), request.getRecipient(),
                                            request.getMessage());
    }
    
    private boolean execCopyRequest(CopyRequest r) {
        this.processCopyRequest(r);
        return true;
    }
    
    private boolean processRequest(Request r) {
        if(r instanceof PostRequest)
            return this.execPostRequest((PostRequest)r);
        else if(r instanceof DeleteRequest)
            return this.execDeleteRequest((DeleteRequest)r);
        else if(r instanceof CopyRequest)
            return this.execCopyRequest((CopyRequest)r);
        else
            return false;
    }


        @Override
    public void run() {
        for (;;) {
            Request r = null;

            try {
                //there's no blocking peek... 
                r = this.requests.take();
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            }

            while (true) {
                if(this.processRequest(r)){     
                    Log.info("RequestHandler: Successfully completed a request! More successful than i'll ever be!");
                    break;
                } else {
                    Log.info("RequestHandler: Couldn't contact another domain. Sleeping for a bit...");
                    try {
                        Thread.sleep(SLEEP_TIME);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }   
            
        }
    }
}
