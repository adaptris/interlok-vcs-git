package com.adaptris.vcs.git.auth;

import static com.adaptris.core.management.vcs.VcsConstants.VCS_SSH_PROXY;
import static com.adaptris.core.management.vcs.VcsConstants.VCS_SSH_PROXY_USERNAME;
import static org.apache.commons.lang.StringUtils.isEmpty;
import static org.apache.commons.lang.StringUtils.trimToEmpty;

import java.util.Properties;

import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig.Host;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;

import com.adaptris.core.management.properties.PropertyResolver;
import com.adaptris.security.password.Password;
import com.jcraft.jsch.ProxyHTTP;
import com.jcraft.jsch.Session;

abstract class AuthenticationProviderImpl implements AuthenticationProvider {

  private String httpProxy = null;
  private String proxyUser = null;
  private String proxyPassword = null;

  protected Properties config;

  public AuthenticationProviderImpl(Properties p) throws Exception {
    config = p;
    setProxyUser(config.getProperty(VCS_SSH_PROXY_USERNAME));
    setProxyPassword(getPasswordProperty(config, VCS_SSH_PROXY_USERNAME));
    setHttpProxy(config.getProperty(VCS_SSH_PROXY));
  }

  protected static String getPasswordProperty(Properties properties, String name) throws Exception {
    String passwordProp = PropertyResolver.getDefaultInstance().resolve(trimToEmpty(properties.getProperty(name)));
    if (!isEmpty(passwordProp)) {
      return Password.decode(passwordProp);
    }
    return passwordProp;
  }

  /**
   * @return the proxy
   */
  String getHttpProxy() {
    return httpProxy;
  }

  /**
   * @param proxy the proxy to set
   */
  void setHttpProxy(String proxy) {
    this.httpProxy = proxy;
  }

  /**
   * @return the proxyUser
   */
  String getProxyUser() {
    return proxyUser;
  }

  /**
   * @param proxyUser the proxyUser to set
   */
  void setProxyUser(String proxyUser) {
    this.proxyUser = proxyUser;
  }

  /**
   * @return the proxyPassword
   */
  String getProxyPassword() {
    return proxyPassword;
  }

  /**
   * @param proxyPassword the proxyPassword to set
   */
  void setProxyPassword(String proxyPassword) {
    this.proxyPassword = proxyPassword;
  }

  @Override
  public TransportConfigCallback getTransportInterceptor() {
    return new TransportConfigCallback() {
      @Override
      public void configure(Transport transport) {
        if (transport instanceof SshTransport) {
          SshTransport sshTransport = (SshTransport) transport;
          sshTransport.setSshSessionFactory(createSessionFactory());
        }
      }
    };
  }



  ProxySupport createSessionFactory() {
    return new ProxySupport();
  }

  protected class ProxySupport extends JschConfigSessionFactory {

    public ProxySupport() {
    }

    @Override
    protected void configure(Host hc, Session session) {
      ProxyHTTP proxy = createProxy();
      if (proxy != null) {
        session.setProxy(createProxy());
      }
    }

    private ProxyHTTP createProxy() {
      ProxyHTTP proxy = null;
      if (!isEmpty(getHttpProxy())) {
        proxy = new ProxyHTTP(getHttpProxy());
        if (!isEmpty(getProxyUser())) {
          proxy.setUserPasswd(getProxyUser(), getProxyPassword());
        }
      }
      return proxy;
    }

  }
}
