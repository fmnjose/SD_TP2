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

    /**
     * Given a postRequest, forwards it to the target domain
     * No logs here because it would be total chaos given that each domain has a thread 
     * for every other domain
     */
    public static List<String> processPostRequest(PostRequest request) throws ProcessingException, MalformedURLException, WebServiceException,
                                                                             MessagesException{
        Response r = null;
        List<String> failedDeliveries = null;
        DomainInfo uri = request.getUri();
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
            System.out.println("execPostRequest: Failed to forward message to " + request.getDomain() + ". Retrying...");
            return false;
        }
        catch (MalformedURLException e) {
            System.out.println("execPostRequest: Bad Url");
            return false;
        } 
        catch (WebServiceException| MessagesException e) {
            System.out.println("execPostRequest: Failed to forward message to " + request.getDomain() + ".");
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
    
        DomainInfo uri = request.getUri();
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
            System.out.println("execDeleteRequest: Failed to redirect request to " + request.getDomain() + ". Retrying...");
            return false;
        } catch (MalformedURLException e) {
            System.out.println("execDeleteRequest: Bad Url");
            return false;
        } catch (WebServiceException e) {
            System.out.println("execDeleteRequest: Failed to forward message to " + request.getDomain() + ".");
            return false;
        } catch (MessagesException me) {
            System.out.println("execDeleteRequest: Error, could not send the message.");
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
                    System.out.println("RequestHandler: Successfully completed request to domain " + r.getDomain()
                            + ". More successful than i'll ever be!");
                    break;
                } else {
                    System.out.println("RequestHandler: Couldn't contact other domain " + r.getDomain() + ". I SLEEP...");
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
