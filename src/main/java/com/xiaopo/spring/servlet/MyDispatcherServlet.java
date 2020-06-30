package com.xiaopo.spring.servlet;

import com.xiaopo.spring.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.util.*;

/**
 * @Description
 * @Author jxp
 **/
public class MyDispatcherServlet extends HttpServlet {

    private static final String LOCATION = "contextConfigLocation";

    private Properties p = new Properties();

    private List<String> classNames = new ArrayList<String>();

    private Map<String,Object> ioc = new HashMap<String,Object>();

    private Map<String,Method> handlerMapping = new HashMap<String, Method>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req,resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            doDispatch(req,resp);
        } catch (Exception e) {
            resp.getWriter().write("500 Exception,details:\r\n"+ Arrays.toString(e.getStackTrace())
                    .replaceAll("\\[|\\]","").replaceAll(",\\s","\r\n"));
        }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        if(this.handlerMapping.isEmpty()){return;}
        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replace(contextPath,"").replaceAll("/+","/");
        if(!this.handlerMapping.containsKey(url)){
            resp.getWriter().write("404 not found!");
            return;
        }

        Map<String,String[]> params = req.getParameterMap();
        Method method = this.handlerMapping.get(url);
        Class<?>[] parameterTypes = method.getParameterTypes();
        Parameter[] parameters = method.getParameters();
        Object[] paramValues = new Object[parameterTypes.length];
        for(int i = 0; i < parameters.length;i++){
            Class<?> type = parameters[i].getType();
            if(type == HttpServletRequest.class){
                paramValues[i] = req;
                continue;
            }else if(type == HttpServletResponse.class){
                paramValues[i] = resp;
                continue;
            }else if(type == String.class){
                if(parameters[i].isAnnotationPresent(MyRequestParam.class)){
                    MyRequestParam myRequestParam = parameters[i].getAnnotation(MyRequestParam.class);
                    if(!"".equals(myRequestParam.value())){
                        String paramName = myRequestParam.value();
                        if(params.get(paramName) == null){
                            resp.getWriter().write("param error");
                            return;
                        }
                        paramValues[i] = Arrays.toString(params.get(paramName))
                                .replaceAll("\\[|\\]","").replaceAll(",\\s",",");
                    }
                }
            }
        }
        String beanName = lowerFirstCase(method.getDeclaringClass().getSimpleName());
        method.invoke(this.ioc.get(beanName),paramValues);
    }

    @Override
    public void init(ServletConfig config){
        //加载配置文件
        doLoadConfig(config.getInitParameter(LOCATION));

        //扫描所有相关的类
        doScanner(p.getProperty("scanPackage"));

        //初始化所有相关类的示例，并保存到IOC容器中
        doInstance();

        //依赖注入
        doAutowired();

        //构造handlerMapping
        initHandlerMapping();

        System.out.println("myServlet init");
    }

    private void doLoadConfig(String location) {
        InputStream stream = this.getClass().getClassLoader().getResourceAsStream(location);
        try {
            p.load(stream);
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if(stream != null){
                try {
                    stream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void doScanner(String scanPackage) {
        URL url = this.getClass().getResource("/"+scanPackage.replaceAll("\\.","/"));
        File dir = new File(url.getFile());
        for(File file: dir.listFiles()){
            if(file.isDirectory()){
                doScanner(scanPackage + "."+ file.getName());
            }else {
                classNames.add(scanPackage + "."+file.getName().replace(".class","").trim());
            }
        }
    }

    private void doInstance() {
        if(classNames.size() == 0){ return;}
        try {
            for(String className: classNames){
                Class<?> clazz = Class.forName(className);
                if(clazz.isAnnotationPresent(MyController.class)){
                    //首字母转小写
                    String beanName = lowerFirstCase(clazz.getSimpleName());
                    ioc.put(beanName,clazz.newInstance());
                }else if(clazz.isAnnotationPresent(MyService.class)){
                    MyService myService = clazz.getAnnotation(MyService.class);
                    String beanName = myService.value();
                    if(!"".equals(beanName.trim())){
                        ioc.put(beanName,clazz.newInstance());
                        continue;
                    }
                    //如果没有设名字，按接口类型创建一个实例
                    Class<?>[] interfaces = clazz.getInterfaces();
                    for(Class<?> i:interfaces){
                        ioc.put(i.getName(),clazz.newInstance());
                    }
                }else {
                    continue;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void doAutowired() {
        if(ioc.isEmpty()){return;}
        for(Map.Entry<String,Object> entry: ioc.entrySet()){
            //给属性注入
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field: fields){
                if(!field.isAnnotationPresent(MyAutoWired.class)){continue;}
                MyAutoWired annotation = field.getAnnotation(MyAutoWired.class);
                String beanName = annotation.value().trim();
                if("".equals(beanName)){
                    beanName = field.getType().getName();
                }
                field.setAccessible(true);
                try {
                    field.set(entry.getValue(),ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                    continue;
                }
            }
        }
    }

    private void initHandlerMapping() {
        if(ioc.isEmpty()){return;}
        for(Map.Entry<String,Object> entry: ioc.entrySet()){
            Class<?> clazz = entry.getValue().getClass();
            if(!clazz.isAnnotationPresent(MyController.class)){return;}
            String baseUrl = "";
            //获取controller的url
            if(clazz.isAnnotationPresent(MyRequestMapping.class)){
                MyRequestMapping myRequestMapping = clazz.getAnnotation(MyRequestMapping.class);
                baseUrl = myRequestMapping.value();
            }

            //获取method的url
            Method[] methods = clazz.getMethods();
            for(Method method: methods){
                if(!method.isAnnotationPresent(MyRequestMapping.class)){continue;}

                MyRequestMapping requestMapping = method.getAnnotation(MyRequestMapping.class);
                String url = ("/"+ baseUrl+"/"+ requestMapping.value()).replaceAll("/+","/");
                handlerMapping.put(url,method);
                System.out.println("mapped "+ url + "," + method);
            }

        }
    }

    private String lowerFirstCase(String str){
        char[] chars = str.toCharArray();
        chars[0]+=32;
        return String.valueOf(chars);
    }
}
