import java.security.Permission; import java.net.SocketPermission;
public class PackageOperations { @SuppressWarnings("removal") public static void main(String[] args) throws Exception {
System.setSecurityManager(new SecurityManager(){@Override public void checkPermission(Permission p){if(p instanceof SocketPermission)throw new SecurityException("PACKAGE_NETWORK_FORBIDDEN");}});
Class.forName("com.zhku.knowledge.ruleagent.RuleAgentOnlineProbeHost").getMethod("main",String[].class).invoke(null,(Object)args);
}}
