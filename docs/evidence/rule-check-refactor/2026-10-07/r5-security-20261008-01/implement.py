from pathlib import Path
import hashlib,json
root=Path(__file__).resolve().parents[5];batch=Path(__file__).resolve().parent
baseline=json.loads((batch/'source-before.json').read_text(encoding='utf-8'))
p=root/baseline['files'][0]['path'];old=p.read_bytes()
assert hashlib.sha256(old).hexdigest()==baseline['files'][0]['sha256']
newline='\r\n' if b'\r\n' in old else '\n'
text=old.decode('utf-8').replace('\r\n','\n')
start=text.index('    @Bean SecurityFilterChain previewSecurityFilterChain(');end=text.index('    static final class Provider',start)
section=text[start:end];body=section[section.index('        // 显式无parent'):section.rfind('    }')]
body=body.replace('PREFIX','prefix').replace('clearCookie(response)','clearCookie(response,cookieName,cookiePath)')
old_routes='''                        .requestMatchers(HttpMethod.POST, prefix + "/auth/logout").authenticated()
                        .requestMatchers(HttpMethod.POST, prefix + "/runs", prefix + "/runs/*/cancel").hasRole("USER")'''
new_routes='''                        .requestMatchers(HttpMethod.POST, prefix + "/auth/logout").authenticated();
                    if (ruleCheck) {
                        // v2只预留查证POST，不继承v1任务、历史、实验或管理路由。
                        access.requestMatchers(HttpMethod.POST,prefix+"/rule-check").hasRole("USER");
                    } else {
                        access.requestMatchers(HttpMethod.POST, prefix + "/runs", prefix + "/runs/*/cancel").hasRole("USER")'''
assert old_routes in body;body=body.replace(old_routes,new_routes)
marker='''                    access.anyRequest().denyAll();'''
assert marker in body;body=body.replace(marker,'''                    }
                    access.anyRequest().denyAll();''')
body=body.replace('new CurrentIdentityFilter(repository, mapper)','new CurrentIdentityFilter(repository, mapper,cookieName,cookiePath)')
body=body.replace('http.addFilterAfter(new ControlledRunRequestLimitFilter(), AuthorizationFilter.class);',
                  'if (!ruleCheck) http.addFilterAfter(new ControlledRunRequestLimitFilter(), AuthorizationFilter.class);')
wrapper='''    @Bean SecurityFilterChain previewSecurityFilterChain(HttpSecurity http, AuthenticationProvider previewAuthenticationProvider,
            PreviewIdentityRepository repository, ObjectMapper mapper, Environment environment) throws Exception {
        boolean conversations="true".equals(environment.getProperty("zhku.controlled-preview.conversations.enabled","false"));
        return buildChain(http,previewAuthenticationProvider,repository,mapper,PREFIX,"ZHKU_CONTROLLED_SESSION",conversations,false);
    }

    /** 只由独立受控配置调用；入口参数来自服务端常量，不能由请求覆盖cookie或路由。 */
    static SecurityFilterChain buildChain(HttpSecurity http,AuthenticationProvider provider,
            PreviewIdentityRepository repository,ObjectMapper mapper,String prefix,String cookieName,
            boolean conversations,boolean ruleCheck) throws Exception {
        String cookiePath="/api"+prefix;
'''
body=body.replace('List.of(previewAuthenticationProvider)','List.of(provider)')
text=text[:start]+wrapper+body+'    }\n\n'+text[end:]
ctor='''        CurrentIdentityFilter(PreviewIdentityRepository repository, ObjectMapper mapper) { this.repository = repository; this.mapper = mapper; }'''
assert ctor in text;text=text.replace(ctor,'''        private final String cookieName,cookiePath;
        CurrentIdentityFilter(PreviewIdentityRepository repository,ObjectMapper mapper,String cookieName,String cookiePath) {
            this.repository=repository;this.mapper=mapper;this.cookieName=cookieName;this.cookiePath=cookiePath;
        }''')
filter_start=text.index('    private static final class CurrentIdentityFilter')
filter_end=text.index('    static void clearCookie',filter_start)
text=text[:filter_start]+text[filter_start:filter_end].replace('clearCookie(response)','clearCookie(response,cookieName,cookiePath)')+text[filter_end:]
cookie='''    static void clearCookie(HttpServletResponse response) {
        response.addHeader("Set-Cookie", ResponseCookie.from("ZHKU_CONTROLLED_SESSION", "").path(COOKIE_PATH)'''
assert cookie in text;text=text.replace(cookie,'''    static void clearCookie(HttpServletResponse response) {
        clearCookie(response,"ZHKU_CONTROLLED_SESSION",COOKIE_PATH);
    }
    private static void clearCookie(HttpServletResponse response,String cookieName,String cookiePath) {
        response.addHeader("Set-Cookie", ResponseCookie.from(cookieName, "").path(cookiePath)''')
p.write_bytes(text.replace('\n',newline).encode('utf-8'))
p=root/'knowledge/src/main/java/com/zhku/knowledge/security/RuleCheckPreviewSecurityConfiguration.java'
text=p.read_text(encoding='utf-8')
text=text.replace('    public static void validateEnvironment(Environment environment) {}','''    public static void validateEnvironment(Environment environment) {
        var profiles=environment.getActiveProfiles();
        if (profiles.length!=1 || !"zhku-rule-check-preview".equals(profiles[0])
                || !"true".equals(environment.getProperty("zhku.rule-check-preview.enabled")))
            throw new IllegalArgumentException("查证身份要求独立profile与显式开关");
        new com.zhku.knowledge.web.LocalPreviewBindingGuard(environment.getProperty("server.address"));
        // 必须检查实际配置；默认值只在守卫中存在并不会替应用创建正确cookie。
        String[][] required={{"server.servlet.context-path","/api"},
                {"server.servlet.session.persistent","false"},{"server.servlet.session.tracking-modes","cookie"},
                {"server.servlet.session.cookie.name",COOKIE_NAME},{"server.servlet.session.cookie.path",COOKIE_PATH},
                {"server.servlet.session.cookie.http-only","true"},{"server.servlet.session.cookie.secure","false"},
                {"server.servlet.session.cookie.same-site","strict"}};
        for (var setting:required) if (!setting[1].equals(environment.getProperty(setting[0])))
            throw new IllegalArgumentException("查证会话配置必须显式绑定独立cookie");
        String timeout=environment.getProperty("server.servlet.session.timeout");
        try {
            if (timeout==null || !java.time.Duration.ofMinutes(15).equals(org.springframework.boot.convert.DurationStyle.detectAndParse(timeout)))
                throw new IllegalArgumentException();
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("查证会话闲置期固定15分钟");
        }
        for (String forbidden:new String[]{"server.servlet.session.cookie.domain","server.servlet.session.cookie.max-age",
                "server.servlet.session.store-dir","spring.session.store-type"})
            if(environment.getProperty(forbidden)!=null)throw new IllegalArgumentException("查证身份禁止域扩展或持久化");
        for (String forbidden:new String[]{"zhku.controlled-preview.enabled","zhku.controlled-preview.conversations.enabled",
                "zhku.local-preview.enabled","zhku.local-admin.enabled","zhku.rag.enabled","zhku.streaming.enabled",
                "zhku.campus-agent.enabled","zhku.campus-agent.archive.enabled"})
            if(!"false".equals(environment.getProperty(forbidden,"false")))
                throw new IllegalArgumentException("查证身份禁止其他预览或任务入口混用");
    }''')
old='return new PreviewSecurityConfiguration().previewSecurityFilterChain(http,ruleCheckAuthenticationProvider,repository,mapper,environment);'
assert old in text;text=text.replace(old,'return PreviewSecurityConfiguration.buildChain(http,ruleCheckAuthenticationProvider,repository,mapper,PREFIX,COOKIE_NAME,false,true);')
p.write_text(text,encoding='utf-8')
print('Shared identity implementation parameterized; v2 explicit cookie/profile guard installed')
