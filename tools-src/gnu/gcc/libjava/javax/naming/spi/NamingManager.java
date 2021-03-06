/* Copyright (C) 2000, 2001 Free Software Foundation
   
   This file is part of libgcj.
   
   This software is copyrighted work licensed under the terms of the
   Libgcj License.  Please consult the file "LIBGCJ_LICENSE" for
   details.  */

package javax.naming.spi;

import java.util.*;
import javax.naming.*;

public class NamingManager
{
  public static final String CPE = "java.naming.spi.CannotProceedException";

  private static InitialContextFactoryBuilder icfb = null;
  private static ObjectFactoryBuilder ofb = null;

  // This class cannot be instantiated.
  NamingManager ()
  {
  }

  public static boolean hasInitialContextFactoryBuilder ()
  {
    return icfb != null;
  }
  
  public static Context getInitialContext (Hashtable environment)
    throws NamingException
  {
    InitialContextFactory icf = null;
    
    if (icfb != null)
      icf = icfb.createInitialContextFactory(environment);
    else
      {	 
	String java_naming_factory_initial = null;
	if (environment != null)
	  java_naming_factory_initial
	    = (String) environment.get (Context.INITIAL_CONTEXT_FACTORY);
	if (java_naming_factory_initial == null)
	  java_naming_factory_initial =
	    System.getProperty (Context.INITIAL_CONTEXT_FACTORY);
	if (java_naming_factory_initial == null)
	  throw new
	    NoInitialContextException ("Can't find property: "
				       + Context.INITIAL_CONTEXT_FACTORY);

	try
	  {
	    icf = (InitialContextFactory) Class.forName (java_naming_factory_initial).newInstance ();
	  }
	catch (Exception exception)
	  {
	    NoInitialContextException e
	      = new NoInitialContextException ("Can't load InitialContextFactory class: "
					       + java_naming_factory_initial);
	    e.setRootCause(exception);
	    throw e;
	  }
      }

    return icf.getInitialContext (environment);
  }

  static Context getURLContext (Object refInfo,
				Name name,
				Context nameCtx,
				String scheme,
				Hashtable environment) 
    throws NamingException
  {
    String prefixes = null;
    if (environment != null)
      prefixes = (String) environment.get (Context.URL_PKG_PREFIXES);
    if (prefixes == null)
      prefixes = System.getProperty (Context.URL_PKG_PREFIXES);
    if (prefixes == null)
      {
	// Specified as the default in the docs.  Unclear if this is
	// right for us.
	prefixes = "com.sun.jndi.url";
      }

    scheme += "URLContextFactory";

    StringTokenizer tokens = new StringTokenizer (prefixes, ":");
    while (tokens.hasMoreTokens ())
      {
	String aTry = tokens.nextToken ();
	try
	  {
	    Class factoryClass = Class.forName (aTry + "." + scheme);
	    ObjectFactory factory =
	      (ObjectFactory) factoryClass.newInstance ();
	    Object obj = factory.getObjectInstance (refInfo, name,
						    nameCtx, environment);
	    Context ctx = (Context) obj;
	    if (ctx != null)
	      return ctx;
	  }
	catch (ClassNotFoundException _1)
	  {
	    // Ignore it.
	  }
	catch (ClassCastException _2)
	  {
	    // This means that the class we found was not an
	    // ObjectFactory or that the factory returned something
	    // which was not a Context.
	  }
	catch (InstantiationException _3)
	  {
	    // If we couldn't instantiate the factory we might get
	    // this.
	  }
	catch (IllegalAccessException _4)
	  {
	    // Another possibility when instantiating.
	  }
	catch (NamingException _5)
	  {
	    throw _5;
	  }
	catch (Exception _6)
	  {
	    // Anything from getObjectInstance.
	  }
      }

    return null;
  }

  public static Context getURLContext (String scheme,
				       Hashtable environment) 
       throws NamingException
  {
    return getURLContext (null, null, null, scheme, environment);
  }

  public static void setObjectFactoryBuilder (ObjectFactoryBuilder builder)
    throws NamingException
  {
    SecurityManager sm = System.getSecurityManager ();
    if (sm != null)
      sm.checkSetFactory ();
    // Once the builder is installed it cannot be replaced.
    if (ofb != null)
      throw new IllegalStateException ("builder already installed");
    if (builder != null)
      ofb = builder;
  }

  static StringTokenizer getPlusPath (String property, Hashtable env,
				      Context nameCtx)
    throws NamingException
  {
    String path = (String) env.get (property);
    if (nameCtx == null)
      nameCtx = getInitialContext (env);
    String path2 = (String) nameCtx.getEnvironment ().get (property);
    if (path == null)
      path = path2;
    else if (path2 != null)
      path += ":" + path2;
    return new StringTokenizer (path, ":");
  }

  public static Object getObjectInstance (Object refInfo,
					  Name name,
					  Context nameCtx,
					  Hashtable environment)
    throws Exception
  {
    ObjectFactory factory = null;

    if (ofb != null)
      factory = ofb.createObjectFactory (refInfo, environment);
    else
      {
	// First see if we have a Reference or a Referenceable.  If so
	// we do some special processing.
	Object ref2 = refInfo;
	if (refInfo instanceof Referenceable)
	  ref2 = ((Referenceable) refInfo).getReference ();
	if (ref2 instanceof Reference)
	  {
	    Reference ref = (Reference) ref2;

	    // If we have a factory class name then we use that.
	    String fClass = ref.getFactoryClassName ();
	    if (fClass != null)
	      {
		// Exceptions here are passed to the caller.
		Class k = Class.forName (fClass);
		factory = (ObjectFactory) k.newInstance ();
	      }
	    else
	      {
		// There's no factory class name.  If the address is a
		// StringRefAddr with address type `URL', then we try
		// the URL's context factory.
		Enumeration e = ref.getAll ();
		while (e.hasMoreElements ())
		  {
		    RefAddr ra = (RefAddr) e.nextElement ();
		    if (ra instanceof StringRefAddr
			&& "URL".equals (ra.getType ()))
		      {
			factory
			  = (ObjectFactory) getURLContext (refInfo,
							   name,
							   nameCtx,
							   (String) ra.getContent (),
							   environment);
			Object obj = factory.getObjectInstance (refInfo,
								name,
								nameCtx,
								environment);
			if (obj != null)
			  return obj;
		      }
		  }

		// Have to try the next step.
		factory = null;
	      }
	  }

	// Now look at OBJECT_FACTORIES to find the factory.
	if (factory == null)
	  {
	    StringTokenizer tokens = getPlusPath (Context.OBJECT_FACTORIES,
						  environment, nameCtx);

	    while (tokens.hasMoreTokens ())
	      {
		String klassName = tokens.nextToken ();
		Class k = Class.forName (klassName);
		factory = (ObjectFactory) k.newInstance ();
		Object obj = factory.getObjectInstance (refInfo, name,
							nameCtx, environment);
		if (obj != null)
		  return obj;
	      }

	    // Failure.
	    return refInfo;
	  }
      }

    if (factory == null)
      return refInfo;
    Object obj = factory.getObjectInstance (refInfo, name,
					    nameCtx, environment);
    return obj == null ? refInfo : obj;
  }

  public static void setInitialContextFactoryBuilder (InitialContextFactoryBuilder builder)
    throws NamingException
  {
    SecurityManager sm = System.getSecurityManager ();
    if (sm != null)
      sm.checkSetFactory ();
    // Once the builder is installed it cannot be replaced.
    if (icfb != null)
      throw new IllegalStateException ("builder already installed");
    if (builder != null)
      icfb = builder;
  }

  public static Context getContinuationContext (CannotProceedException cpe)
    throws NamingException
  {
    Hashtable env = cpe.getEnvironment ();
    if (env != null)
      env.put (CPE, cpe);

    // It is really unclear to me if this is right.
    try
      {
	Object obj = getObjectInstance (null, cpe.getAltName (),
					cpe.getAltNameCtx (), env);
	if (obj != null)
	  return (Context) obj;
      }
    catch (Exception _)
      {
      }

    throw cpe;
  }

  public static Object getStateToBind (Object obj, Name name,
				       Context nameCtx, Hashtable environment)
    throws NamingException
  {
    StringTokenizer tokens = getPlusPath (Context.STATE_FACTORIES,
					  environment, nameCtx);
    while (tokens.hasMoreTokens ())
      {
	String klassName = tokens.nextToken ();
	try
	  {
	    Class k = Class.forName (klassName);
	    StateFactory factory = (StateFactory) k.newInstance ();
	    Object o = factory.getStateToBind (obj, name, nameCtx,
					       environment);
	    if (o != null)
	      return o;
	  }
	catch (ClassNotFoundException _1)
	  {
	    // Ignore it.
	  }
	catch (ClassCastException _2)
	  {
	    // This means that the class we found was not an
	    // ObjectFactory or that the factory returned something
	    // which was not a Context.
	  }
	catch (InstantiationException _3)
	  {
	    // If we couldn't instantiate the factory we might get
	    // this.
	  }
	catch (IllegalAccessException _4)
	  {
	    // Another possibility when instantiating.
	  }
      }

    return obj;
  }
}
