package sd1920.trab2.api.proxy;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import sd1920.trab2.api.rest.UserServiceRest;

@Path(UserServiceRest.PATH)
public interface UserServiceProxy extends UserServiceRest {
    
    /**
     * Gets a User from a proxy resource, in Proxy form (with the inbox)
     * @param name
     * @param secret
     * @return UserProxy object
     */
	@GET
	@Path("/{name}/proxy")
	@Produces(MediaType.APPLICATION_JSON)
	UserProxy getUserProxy(@PathParam("name") String name, @QueryParam("secret") String secret);
}