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
import sd1920.trab2.server.rest.resources.MessageResourceRest;

/**
 * Used for asynchronously launching requests to other servers
 */
public class RequestHandler implements Runnable {

    private static final int SLEEP_TIME = 200;

    protected static Client client;
    private ServerMessageUtils utils;
    private static Logger Log = ServerMessageUtils.Log;

    private BlockingQueue<Request> requests;

    public RequestHandler(ClientConfig config, ServerMessageUtils utils) {
        client = ClientBuilder.newClient(config);

        this.utils = utils;

        this.requests = new LinkedBlockingQueue<>();
    }

    public void addRequest(Request request) {
        synchronized(this.requests){
            this.requests.add(request);
        }
    }

    /**
     * Given a postRequest, forwards it to the target domain
     * No logs here because it would be total chaos given that each domain has a thread 
     * for every other domain
     */
    public static List<String> processPostRequest(PostRequest request) throws ProcessingException, MalformedURLException, WebServiceException{
        Response r = null;
        List<String> failedDeliveries = null;
        DomainInfo uri = request.getUri();
        Message msg = request.getMessage();

        if (uri.isRest()) {            
            WebTarget target = client.target(uri.getUri()).path(MessageServiceRest.PATH).path("mbox");
            
            r = target.request().accept(MediaType.APPLICATION_JSON)
                .post(Entity.entity(msg, MediaType.APPLICATION_JSON));

            failedDeliveries = r.readEntity(new GenericType<List<String>>() {});
        } else {
            MessageServiceSoap msgService = null;

            Service service = Service.create(new URL(uri.getUri() + ServerMessageUtils.MESSAGES_WSDL),
                    ServerMessageUtils.MESSAGE_QNAME);

            msgService = service.getPort(MessageServiceSoap.class);
            
            ((BindingProvider) msgService).getRequestContext().put(BindingProviderProperties.CONNECT_TIMEOUT,
                    ServerMessageUtils.TIMEOUT);
            ((BindingProvider) msgService).getRequestContext().put(BindingProviderProperties.REQUEST_TIMEOUT,
                    ServerMessageUtils.TIMEOUT);
              
            failedDeliveries = msgService.postForwardedMessage(msg);
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
        catch (WebServiceException e) {
            Log.info("execPostRequest: Failed to forward message to " + request.getDomain() + ".");
            return false;
        }

        String senderName = ServerMessageUtils.getSenderCanonicalName(request.getMessage().getSender());

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
    
        DomainInfo uri = request.getUri();
        String mid = request.getMid();

        if (uri.isRest()) {
            WebTarget target = client.target(uri.getUri()).path(MessageResourceRest.PATH).path("msg");

            target.path(mid).request().delete();
        } else {
            MessageServiceSoap msgService = null;
            
            Service service = Service.create(new URL(uri.getUri() + ServerMessageUtils.MESSAGES_WSDL),
                    ServerMessageUtils.MESSAGE_QNAME);
            msgService = service.getPort(MessageServiceSoap.class);
            
            ((BindingProvider) msgService).getRequestContext().put(BindingProviderProperties.CONNECT_TIMEOUT,
                    ServerMessageUtils.TIMEOUT);
            ((BindingProvider) msgService).getRequestContext().put(BindingProviderProperties.REQUEST_TIMEOUT,
                    ServerMessageUtils.TIMEOUT);
            
            msgService.deleteForwardedMessage(Long.valueOf(mid));
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
                if (r instanceof PostRequest ? this.execPostRequest((PostRequest) r)
                        : this.execDeleteRequest((DeleteRequest) r)) {     
                    Log.info("RequestHandler: Successfully completed request to domain " + r.getDomain()
                            + ". More successful than i'll ever be!");
                    break;
                } else {
                    Log.info("RequestHandler: Couldn't contact other domain " + r.getDomain() + ". I SLEEP...");
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
