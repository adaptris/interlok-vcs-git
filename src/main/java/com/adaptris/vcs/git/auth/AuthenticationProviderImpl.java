package com.adaptris.vcs.git.auth;

import static com.adaptris.core.management.vcs.VcsConstants.VCS_SSH_PROXY;
import static com.adaptris.core.management.vcs.VcsConstants.VCS_SSH_PROXY_USERNAME;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;

import java.util.Properties;

import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig.Host;
import org.eclipse.jgit.transport.SshTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adaptris.core.management.properties.PropertyResolver;
import com.adaptris.security.password.Password;
import com.adaptris.vcs.git.GitVCS;
import com.adaptris.vcs.git.api.JGitApi;
import com.jcraft.jsch.Proxy;
import com.jcraft.jsch.ProxyHTTP;
import com.jcraft.jsch.ProxySOCKS4;
import com.jcraft.jsch.ProxySOCKS5;
import com.jcraft.jsch.Session;

abstract class AuthenticationProviderImpl implements AuthenticationProvider {

  private String proxy = null;
  private String proxyType = null;
  private String proxyUser = null;
  private String proxyPassword = null;
  protected static final Logger log = LoggerFactory.getLogger(JGitApi.class);

  protected Properties config;

  public AuthenticationProviderImpl(Properties p) throws Exception {
    config = p;
    setProxyUser(config.getProperty(VCS_SSH_PROXY_USERNAME));
    setProxyPassword(getPasswordProperty(config, VCS_SSH_PROXY_USERNAME));
    setProxy(config.getProperty(VCS_SSH_PROXY));
    setProxyType(config.getProperty(GitVCS.VCS_PROXY_TYPE));
  }

  protected static String getPasswordProperty(Properties properties, String name) throws Exception {
    String passwordProp = PropertyResolver.getDefaultInstance().resolve(trimToEmpty(properties.getProperty(name)));
    if (!isEmpty(passwordProp)) {
      return Password.decode(passwordProp);
    }
    return passwordProp;
  }

  String getProxy() {
    return proxy;
  }

  void setProxy(String proxy) {
    this.proxy = proxy;
  }

  String getProxyUser() {
    return proxyUser;
  }

  void setProxyUser(String proxyUser) {
    this.proxyUser = proxyUser;
  }

  String getProxyPassword() {
    return proxyPassword;
  }

  void setProxyPassword(String proxyPassword) {
    this.proxyPassword = proxyPassword;
  }

  String getProxyType() {
    return proxyType;
  }

  void setProxyType(String proxyType) {
    this.proxyType = proxyType;
  }

  @Override
  public TransportConfigCallback getTransportInterceptor() {
    return transport -> {
      if (transport instanceof SshTransport) {
        SshTransport sshTransport = (SshTransport) transport;
        sshTransport.setSshSessionFactory(createSessionFactory());
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
      if (!isEmpty(getProxy())) {
        ProxyBuilder builder = ProxyBuilder.parse(getProxyType());
        Proxy actualProxy = builder.build(getProxy());
        if (!isEmpty(getProxyUser())) {
          builder.addUserCredentials(actualProxy, getProxyUser(), getProxyPassword());
        }
        session.setProxy(actualProxy);
      }
    }
  }

  enum ProxyBuilder {
    HTTP {
      @Override
      Proxy build(String proxy) {
        // log.trace("HTTP Proxy : {}", proxy);
        return new ProxyHTTP(proxy);
      }

      @Override
      Proxy addUserCredentials(Proxy proxy, String user, String passwd) {
        ((ProxyHTTP) proxy).setUserPasswd(user, passwd);
        return proxy;
      }
    },
    SOCKS5 {

      @Override
      Proxy build(String proxy) {
        // log.trace("SOCKS5 Proxy : {}", proxy);
        return new ProxySOCKS4(proxy);
      }

      @Override
      Proxy addUserCredentials(Proxy proxy, String user, String passwd) {
        ((ProxySOCKS4) proxy).setUserPasswd(user, passwd);
        return proxy;
      }

    },
    SOCKS4 {

      @Override
      Proxy build(String proxy) {
        // log.trace("SOCKS4 Proxy : {}", proxy);
        return new ProxySOCKS5(proxy);
      }

      @Override
      Proxy addUserCredentials(Proxy proxy, String user, String passwd) {
        ((ProxySOCKS5) proxy).setUserPasswd(user, passwd);
        return proxy;
      }
    };

    public static ProxyBuilder parse(String type) {
      if (isBlank(type)) {
        return HTTP;
      }
      return ProxyBuilder.valueOf(type.toUpperCase());
    }

    abstract Proxy build(String proxy);

    abstract Proxy addUserCredentials(Proxy proxy, String user, String password);

  }

}
