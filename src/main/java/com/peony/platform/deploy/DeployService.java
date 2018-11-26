package com.peony.platform.deploy;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.jcraft.jsch.*;
import com.peony.engine.framework.cluster.ServerInfo;
import com.peony.engine.framework.control.annotation.Service;
import com.peony.engine.framework.control.gm.Gm;
import com.peony.engine.framework.data.DataService;
import com.peony.engine.framework.security.exception.ToClientException;
import com.peony.engine.framework.server.Server;
import com.peony.engine.framework.server.SysConstantDefine;
import com.peony.engine.framework.tool.util.Util;
import com.peony.platform.deploy.util.FileProgressMonitor;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service(destroy = "destroy")
public class DeployService {
    private static final Logger logger = LoggerFactory.getLogger(DeployService.class);
    private DataService dataService;

    public final int serverPageSize = 10;

    final String endStr = "command end exit";

    private ConcurrentLinkedQueue<String> logQueue = new ConcurrentLinkedQueue<>();
    private ConcurrentHashMap<Integer,String> deployStateMap = new ConcurrentHashMap<>(); // 各个服务器部署的状态提示
//    private ConcurrentHashMap<Integer,String> deployStateMap = new ConcurrentHashMap<>(); // 各个服务器部署的状态提示

    @Gm(id = "deploytest")
    public void gm(){
        //

    }

    public static void main(String[] args) throws Exception{

//        List<ServerInfo> serverInfos = new ArrayList<>();
//        ServerInfo serverInfo = new ServerInfo();
//        serverInfo.setId(1);
//        serverInfos.add(serverInfo);
////        serverInfo.setInnerHost();
//        new DeployService().deployLocal("test","/usr/my",serverInfos);


        //
//        new DeployService().deployGit("myfruit","https://github.com/xuerong/PeonyFramwork.wiki.git",null,null,"master");

    }

    public JSONObject getCodeOriginList(String projectId){
        List<CodeOrigin> codeOrigins = dataService.selectList(CodeOrigin.class,"projectId=?",projectId);
        JSONArray array = new JSONArray();
        for(CodeOrigin codeOrigin : codeOrigins){
            array.add(codeOrigin.toJson());
        }
        JSONObject ret = new JSONObject();
        ret.put("codeOrigins",array);
        return ret;
    }

    public JSONObject addCodeOrigin(String projectId,int id,String name,int type,String params){
        CodeOrigin codeOrigin = dataService.selectObject(CodeOrigin.class,"projectId=? and id=?",projectId,id);
        if(codeOrigin != null){
            throw new ToClientException(SysConstantDefine.InvalidParam,"codeOrigin has exist ,projectId={},id={}",projectId,id);
        }
        codeOrigin = new CodeOrigin();
        codeOrigin.setProjectId(projectId);
        codeOrigin.setName(name);
        codeOrigin.setId(id);
        codeOrigin.setType(type);
        codeOrigin.setParams(params);
        dataService.insert(codeOrigin);
        return getCodeOriginList(projectId);
    }
    public JSONObject delCodeOrigin(String projectId,int id){
        CodeOrigin codeOrigin = dataService.selectObject(CodeOrigin.class,"projectId=? and id=?",projectId,id);
        if(codeOrigin == null){
            throw new ToClientException(SysConstantDefine.InvalidParam,"codeOrigin is not exist ,projectId={},id={}",projectId,id);
        }
        dataService.delete(codeOrigin);
        // 删除对应的部署类型
        List<DeployType> deployTypes = dataService.selectList(DeployType.class,"projectId=? and codeOrigin=?",projectId,id);
        for(DeployType deployType:deployTypes){
            dataService.delete(deployType);
        }
        return getCodeOriginList(projectId);
    }


    public JSONObject getDeployProject(){
        List<DeployProject> deployProjects = dataService.selectList(DeployProject.class,"");
        JSONArray array = new JSONArray();
        for(DeployProject deployProject : deployProjects){
            array.add(deployProject.toJson());
        }
        JSONObject ret = new JSONObject();
        ret.put("deployProjects",array);
        return ret;
    }

    public JSONObject setDeployProject(String projectId,String name,String defaultSource,String sourceParam){
        DeployProject deployProject = dataService.selectObject(DeployProject.class,"projectId=?",projectId);
        if(deployProject == null){
            throw new ToClientException(SysConstantDefine.InvalidParam,"project is not exist ,projectId={}",projectId);
        }
        deployProject.setName(name);
        deployProject.setDefaultSource(defaultSource);
        deployProject.setSourceParam(sourceParam);
        dataService.update(deployProject);
        return getDeployProject();
    }

    public JSONObject addDeployProject(String projectId,String name,String defaultSource,String sourceParam){
        /**
         * private String projectId;
         private String name;
         private String defaultSource;
         @Column(stringColumnType = StringTypeCollation.Text)
         private String sourceParam;
         */
        DeployProject deployProject = dataService.selectObject(DeployProject.class,"projectId=?",projectId);
        if(deployProject != null){
            throw new ToClientException(SysConstantDefine.InvalidParam,"project has exist ,projectId={}",projectId);
        }
        deployProject = new DeployProject();
        deployProject.setProjectId(projectId);
        deployProject.setName(name);
        deployProject.setDefaultSource(defaultSource);
        deployProject.setSourceParam(sourceParam);
        dataService.insert(deployProject);
        return getDeployProject();
    }
    public JSONObject delDeployProject(String projectId){

        DeployProject deployProject = dataService.selectObject(DeployProject.class,"projectId=?",projectId);
        if(deployProject == null){
            throw new ToClientException(SysConstantDefine.InvalidParam,"project is not exist ,projectId={}",projectId);
        }
        dataService.delete(deployProject);
        // 删除其它：
        List<CodeOrigin> codeOrigins = dataService.selectList(CodeOrigin.class,"projectId=?",projectId);
        for(CodeOrigin codeOrigin: codeOrigins){
            dataService.delete(codeOrigin);
        }
        List<DeployType> deployTypes = dataService.selectList(DeployType.class,"projectId=?",projectId);
        for(DeployType deployType: deployTypes){
            dataService.delete(deployType);
        }
        List<DeployServer> deployServers = dataService.selectList(DeployServer.class,"projectId=?",projectId);
        for(DeployServer deployServer: deployServers){
            dataService.delete(deployServer);
        }
        return getDeployProject();
    }

    public JSONObject getDeployTypes(String projectId){
        List<DeployType> deployTypes = dataService.selectList(DeployType.class,"projectId=?",projectId);
        JSONArray jsonArray = new JSONArray();
        for(DeployType deployType:deployTypes){
            jsonArray.add(deployType.toJson());
        }
        JSONObject ret = new JSONObject();
        ret.put("deployTypes",jsonArray);
        return ret;
    }

    public JSONObject addDeployType(String projectId,String id,String name,int codeOrigin,String env,String buildParams,int restart){
        checkParams(projectId,id,name,env);
        DeployProject deployProject = dataService.selectObject(DeployProject.class,"projectId=?",projectId);
        if(deployProject == null){
            throw new ToClientException(SysConstantDefine.InvalidParam,"project is not exist ,projectId={}",projectId);
        }
        DeployType deployType = dataService.selectObject(DeployType.class,"projectId=? and id=?",projectId,id);
        if(deployType != null){
            throw new ToClientException(SysConstantDefine.InvalidParam,"id重复");
        }

        deployType = new DeployType();
        deployType.setProjectId(projectId);
        deployType.setId(id);
        deployType.setName(name);
        deployType.setCodeOrigin(codeOrigin);
        deployType.setBuildParams(buildParams);
        deployType.setEnv(env);
        deployType.setRestart(restart);
        dataService.insert(deployType);
        return getDeployTypes(projectId);
    }
    public JSONObject delDeployType(String projectId,String id){
        checkParams(projectId,id);
        DeployProject deployProject = dataService.selectObject(DeployProject.class,"projectId=?",projectId);
        if(deployProject == null){
            throw new ToClientException(SysConstantDefine.InvalidParam,"project is not exist ,projectId={}",projectId);
        }
        DeployType deployType = dataService.selectObject(DeployType.class,"projectId=? and id=?",projectId,id);
        if(deployType == null){
            throw new ToClientException(SysConstantDefine.InvalidParam,"deploy type is not exist!projectId=? and id=?",projectId,id);
        }
        dataService.delete(deployType);
        return getDeployTypes(projectId);
    }

    public JSONObject getDeployServerList(String projectId,int pageNum,int pageSize){
        List<DeployServer> deployServers = dataService.selectListBySql(DeployServer.class,"select * from deployserver where projectId=? order by id limit ?,?",projectId,pageSize*pageNum,pageSize);
        JSONObject ret = new JSONObject();
        JSONArray array = new JSONArray();
        for(DeployServer deployServer: deployServers){
            array.add(deployServer.toJson());
        }
        ret.put("deployServers",array);
        return ret;
    }

    public void deleteServer(int id){
        ServerInfo serverInfo = dataService.selectObject(ServerInfo.class,"id=?",id);
        if(serverInfo == null){
            throw new ToClientException(SysConstantDefine.InvalidParam,"server id is not exist! id={}",id);
        }
        dataService.delete(serverInfo);
    }


    public JSONObject addDeployServer(String projectId, int id, String name, String sshIp, String sshUser, String sshPassword, String path){

        DeployServer deployServer = dataService.selectObject(DeployServer.class,"projectId=? and id=?",projectId,id);
        if(deployServer != null){
            throw new ToClientException(SysConstantDefine.InvalidParam,"server id has exist!projectId={}, id={}",projectId,id);
        }
        deployServer = new DeployServer();
        deployServer.setProjectId(projectId);
        deployServer.setId(id);
        deployServer.setName(name);
        deployServer.setSshIp(sshIp);
        deployServer.setSshUser(sshUser);
        deployServer.setSshPassword(sshPassword);
        deployServer.setPath(path);
        dataService.insert(deployServer);
        JSONObject ret = getDeployServerList(projectId,0,serverPageSize);
        JSONArray array = ret.getJSONArray("deployServers");
        boolean have = false;
        for(Object object: array){
            JSONObject jsonObject = (JSONObject)object;
            if(jsonObject.getInteger("id")==id){
                have = true;
            }
        }
        if(!have){
            array.add(deployServer.toJson());
        }

        return ret;
    }

    public JSONObject delDeployServer(String projectId, int id,int page){

        DeployServer deployServer = dataService.selectObject(DeployServer.class,"projectId=? and id=?",projectId,id);
        if(deployServer == null){
            throw new ToClientException(SysConstantDefine.InvalidParam,"server id is not exist!projectId={}, id={}",projectId,id);
        }
        dataService.delete(deployServer);

        JSONObject ret = getDeployServerList(projectId,page,serverPageSize);
        JSONArray array = ret.getJSONArray("deployServers");
        Iterator<Object> it = array.iterator();
        while (it.hasNext()){
            JSONObject jsonObject = (JSONObject)it.next();
            if(jsonObject.getInteger("id")==id){
                it.remove();
                break;
            }
        }

        return ret;
    }

    public JSONObject doDeploy(String projectId,int deployId,String serverIds){
        checkParams(projectId,serverIds);
        DeployProject deployProject = dataService.selectObject(DeployProject.class,"projectId=?",projectId);
        if(deployProject == null){
            throw new ToClientException(SysConstantDefine.InvalidParam,"project is not exist ,projectId={}",projectId);
        }

        DeployType deployType = dataService.selectObject(DeployType.class,"projectId=? and id=?",projectId,deployId);
        if(deployType == null){
            throw new ToClientException(SysConstantDefine.InvalidParam,"deploy type is not exist!projectId={},deployId={}",projectId,deployId);
        }
        //
        CodeOrigin codeOrigin = dataService.selectObject(CodeOrigin.class,"projectId=? and id=?",projectId,deployType.getCodeOrigin());
        if(codeOrigin == null){
            throw new ToClientException(SysConstantDefine.InvalidParam,"code origin  is not exist!projectId={},deployId={},codeOrigin={}",projectId,deployId,deployType.getCodeOrigin());
        }
        //
        switch (codeOrigin.getType()){ // 1本地，2git，3svn
            case 1:
                break;
            case 2:
                // git拉取(如果不存在，则clone，否则，fetch)，打包压缩，多线程连接不同服务器，上传
                /**
                 * params.put("gitPath",gitPath);
                 params.put("gitBranch",gitBranch);
                 params.put("gitName",gitName);
                 params.put("gitPassword",gitPassword);

                 String name,String gitUrl,String credentialsName,String credentialsPassword,String branch)
                 */
                JSONObject params = JSONObject.parseObject(codeOrigin.getParams());

                DeployState deployState = getDeployState(projectId,deployId);
                if(deployState.running.compareAndSet(false,true)){
                    // 从git拉取
                    deployState.stateInfo.put("state",1);
                    String projectUrl = deployGit(params.getString("gitPath"),params.getString("gitBranch"),params.getString("gitName"),params.getString("gitPassword"),deployState);
                    // 打包和部署
                    try{
                        deployLocal(projectId,deployType.getEnv(),projectUrl,serverIds,deployState);
                    }catch (Exception e){
                        logger.error("",e);
                    }


                    // 结束
                    deployState.reset();
                }else{
                    throw new ToClientException(SysConstantDefine.InvalidParam,"on deploy,不能重复部署");
                }
                break;
            case 3:
                break;
        }
        return new JSONObject();

    }


    public JSONObject getServerList(){
        return getServerList(Integer.MIN_VALUE,Integer.MAX_VALUE);
    }
    public JSONObject getServerList(int start,int end){

        List<ServerInfo> serverInfos = dataService.selectListBySql(ServerInfo.class,"select * from serverinfo where id>=? and id<=?",start,end);

        JSONObject ret = new JSONObject();
        JSONArray array = new JSONArray();
        for(ServerInfo serverInfo : serverInfos){
            array.add(serverInfo.toJson());
        }
        ret.put("serverInfos",array);
        return ret;
    }

    private void checkParams(String... params){
        for(String param : params){
            if(StringUtils.isEmpty(param)){
                throw new ToClientException(SysConstantDefine.InvalidParam,"param error,is empty!");
            }
        }
    }



    /**
     * 1、代码来源：从git取（填写git目录），本地直接上传，从svn取，
     * 2、从git或svn取下来，需要列出env下面的目录，作为build的参数
     * 3、执行build
     * 4、上传（压缩吗？sftp还是async？）并启动
     *
     *
     * gradle  build_param -P env=test;
     */
    public String deployGit(String gitUrl,String branch,String credentialsName,String credentialsPassword,DeployState deployState){
        try{
            String projectUrl = System.getProperty("user.dir");

            projectUrl = projectUrl.replace("PeonyFramwork","");

            String localPath = projectUrl+"deploy/git/"+Math.abs(gitUrl.hashCode());
            // 更新本地代码
            gitFetchCode(gitUrl,localPath,branch,credentialsName,credentialsPassword,deployState);
            // 压缩



//            CloneCommand cloneCommand = Git.cloneRepository();
//            if(StringUtils.isNotEmpty(credentialsName)){
//                cloneCommand.setCredentialsProvider(new UsernamePasswordCredentialsProvider(credentialsName, credentialsPassword));
//            }
//            cloneCommand.setURI(gitUrl)
//                    .setDirectory(new File(projectUrl+"/deploy/git/"+name))
//                    .setBranch(branch)
//                    .setProgressMonitor(new GitMonitor())
//                    .call();

//            Repository rep = new FileRepository("/Users/zhengyuzhenelex/Documents/testgit/.git");
//            Git git = new Git(rep);
//            git.pull().setRemote("origin").call();
            //fetch命令提供了setRefSpecs方法，而pull命令并没有提供，所有pull命令只能fetch所有的分支
//        git.fetch().setRefSpecs("refs/heads/*:refs/heads/*").call();
            return localPath;
        }catch (Exception e){
            logger.error("",e);
        }
        return null;
    }



    private void gitFetchCode(String gitUrl,String localPath,String branchName,String credentialsName,String credentialsPassword,DeployState deployState){
        try{
            if (new File(localPath + "/.git").exists()) {
                Git git = Git.open(new File(localPath));
                //检测dev分支是否已经存在 若不存在则新建分支
                List<Ref> localBranch = git.branchList().call();
                boolean isCreate = true;
                for (Ref branch : localBranch) {
                    System.out.println(branch.getName());
                    if (branch.getName().endsWith(branchName)) {
                        isCreate = false;
                        break;
                    }
                }
                git.checkout().setCreateBranch(isCreate).setName(branchName).setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
                        .setProgressMonitor(new GitMonitor("切换分支"+branchName,deployState)).call();
                PullCommand pullCommand = git.pull();
                if(StringUtils.isNotEmpty(credentialsName)){
                    pullCommand.setCredentialsProvider(new UsernamePasswordCredentialsProvider(credentialsName, credentialsPassword));
                }
                pullCommand.setProgressMonitor(new GitMonitor("拉取分支"+branchName,deployState)).call();
            } else {
                List<String> remoteBranch = new ArrayList<>();
                remoteBranch.add("master");
                CloneCommand cloneCommand = Git.cloneRepository();
                if(StringUtils.isNotEmpty(credentialsName)){
                    cloneCommand.setCredentialsProvider(new UsernamePasswordCredentialsProvider(credentialsName, credentialsPassword));
                }
                Git git =cloneCommand.setURI(gitUrl).setBranchesToClone(remoteBranch).
                        setDirectory(new File(localPath)).setProgressMonitor(new GitMonitor("克隆项目",deployState)).call();
                git.checkout().setCreateBranch(true).setName(branchName).setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
                        .setProgressMonitor(new GitMonitor("切换分支"+branchName,deployState)).call();
                PullCommand pullCommand = git.pull();
                if(StringUtils.isNotEmpty(credentialsName)){
                    pullCommand.setCredentialsProvider(new UsernamePasswordCredentialsProvider(credentialsName, credentialsPassword));
                }
                pullCommand.setProgressMonitor(new GitMonitor("拉取分支"+branchName,deployState)).call();
            }
        }catch (Throwable e){
            logger.error("",e);
        }
    }

    class GitMonitor implements ProgressMonitor {
        private String des;
        private DeployState deployState;
        int completed=0;
        public GitMonitor(String des,DeployState deployState){
            this.des = des;
            this.deployState = deployState;
        }
        @Override
        public void start(int totalTasks) {
            System.out.println("start:"+totalTasks);
            deployState.log.add(des+" start,totalTasks="+totalTasks);
        }

        @Override
        public void beginTask(String title, int totalWork) {
            System.out.println("beginTask:title="+title+",totalWork="+totalWork);
            deployState.log.add(des+"beginTask:title="+title+",totalWork="+totalWork);
            completed=0;
        }

        @Override
        public void update(int completed) {
            System.out.println("update:"+completed);
            this.completed += completed;
//            stateInfo.put("update",this.completed);
        }

        @Override
        public void endTask() {
            System.out.println("endTask");
            deployState.log.add(des+"endTask");
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    }

    private Map<String,Map<Integer,DeployState>> deployStates = new ConcurrentHashMap<>();

    public JSONObject getDeployStateForClient(String projectId,int deployId,int logRow){
        return getDeployState(projectId, deployId).toClientJson(logRow);
    }
    private DeployState getDeployState(String projectId,int deployId){
        Map<Integer,DeployState> map =  deployStates.get(projectId);
        if(map == null){
            map = new ConcurrentHashMap<>();
            deployStates.putIfAbsent(projectId,map);
            map = deployStates.get(projectId);
        }
        DeployState deployState = map.get(deployId);
        if(deployState == null){
            deployState = new DeployState();
            deployState.projectId = projectId;
            deployState.deployId = deployId;
            map.putIfAbsent(deployId,deployState);
            deployState = map.get(deployId);
        }
        return deployState;
    }

    public void destroy(){

    }


    /**
     * 部署的状态
     * 是否正在部署，部署的信息
     */
    class DeployState{
        private String projectId;
        private int deployId;
        private volatile AtomicBoolean running = new AtomicBoolean(false); // 当前的状态，同步代码，打包，部署具体服，
        private JSONObject stateInfo = new JSONObject(); // 这里面存储的状态
        private JSONArray log = new JSONArray(); // 这里面存储的是日志

        private void reset(){
            running.set(false);
            stateInfo = new JSONObject();
            log = new JSONArray();
        }

        public JSONObject toClientJson(int logRow){
            JSONObject ret = stateInfo;
            int size = log.size();
            if(size>logRow){
                ret.put("log",new JSONArray(log.subList(logRow,size-1)));
                ret.put("logRow",size);
            }else{
                ret.put("log",new JSONArray());
                ret.put("logRow",logRow);
            }
            return ret;
        }
    }

    public void deployLocal(String projectId,String env,String projectUrl,String serverIds,DeployState deployState)throws Exception{
        deployStateMap.clear();

        deployState.stateInfo.put("state",2);
        // 本地编译
//        String projectUrl = System.getProperty("user.dir");

        StringBuilder cmd = new StringBuilder("cd "+projectUrl+" \n");
        cmd.append("pwd \n");
        cmd.append("echo build... \n");
        cmd.append("gradle  build_param -P env="+env+" \n");
        //tar -xzvf im_toby.tar.gz
        cmd.append("cd "+projectUrl+"/build \n");
        cmd.append("echo tar begin... \n");
        cmd.append("tar -czvf "+projectUrl+"/build/target.tar.gz "+"./target \n"); // TODO 最后要删除
        cmd.append("echo tar finish... \n");

        System.out.println(cmd);

        String[] cmds = {"/bin/sh","-c",cmd.toString()};

        Process pro = Runtime.getRuntime().exec(cmds);
//        pro.waitFor();
        InputStream in = pro.getInputStream();
        BufferedReader read = new BufferedReader(new InputStreamReader(in));
        String line = null;
        while((line = read.readLine())!=null){
            System.out.println(line);
            logQueue.offer(line);
            deployState.log.add(line);
        }
        System.out.println("-------------------------------");
        Thread.sleep(500);
        logQueue.offer(endStr);
        deployState.log.add(endStr);
        // sudo launchctl load -w /System/Library/LaunchDaemons/ssh.plist
        // sudo launchctl list | grep ssh


        deployState.stateInfo.put("state",3);

        List<String> list = Util.split2List(serverIds,String.class);
        StringBuilder sb = new StringBuilder();
        String sp = "";
        for(String _serverId:list){
            if(_serverId.contains("-")){
                List<Integer> fromTo =  Util.split2List(_serverId,Integer.class,"-");
                for(int i=fromTo.get(0);i<=fromTo.get(1);i++){
                    sb.append(sp+i);
                    sp=",";
                }
            }else{
                sb.append(sp+Integer.parseInt(_serverId));
                sp=",";
            }
        }
        String sql = "select * from deployserver where projectId=? and id in ("+sb.toString()+")";
        List<DeployServer> deployServers = dataService.selectListBySql(DeployServer.class,sql,projectId);

        ExecutorService executorService =Executors.newFixedThreadPool(deployServers.size()<32?deployServers.size():32);

        //
        JSONArray array = new JSONArray();
        CountDownLatch latch = new CountDownLatch(deployServers.size());
        // 上传
        for(final DeployServer deployServer : deployServers){
            final  JSONObject object = new JSONObject();
            object.put("serverId",deployServer.getId());
            array.add(object);
            executorService.execute(()->{
                Session session = null;
                try{
                    deployStateMap.put(deployServer.getId(),"开始连接");
                    object.put("st","1");
                    // 连接
//                    Session session  = this.connect("localhost","郑玉振elex",22,"zhengyuzhen");
//                    Session session  = connect("47.93.249.150","root",22,"Zyz861180416");
                    session  = connect(deployServer.getSshIp(),deployServer.getSshUser(),22,deployServer.getSshPassword());
            //            Session session  = this.connect(deployServer.getSshIp(),deployServer.getSshUser(),22,deployServer.getSshPassword());
                    System.out.println("isConnected:"+session!=null);

                    // 创建目录
                    deployStateMap.put(deployServer.getId(),"创建目录");
                    object.put("st","2");

                    StringBuilder uploadCmds = new StringBuilder();
                    uploadCmds.append("mkdir -p "+deployServer.getPath()+" \n");
                    uploadCmds.append("cd "+deployServer.getPath()+" \n");
                    execCmd(session,uploadCmds.toString(),object,deployState);
                    // 上传
                    deployStateMap.put(deployServer.getId(),"正在上传");
//                    object.put("des","正在上传");
                    upload(session,deployServer.getPath()+"/target.tar.gz",projectUrl+"/build/target.tar.gz",new DeployProgressSetter(object,deployStateMap,deployServer.getId()));

                    // 解压并执行
                    deployStateMap.put(deployServer.getId(),"解压并执行");
                    object.put("st","3");
                    StringBuilder execCmds = new StringBuilder();
                    execCmds.append("cd "+deployServer.getPath()+" \n");
                    execCmds.append("tar -xzvf target.tar.gz \n");
                    execCmds.append("cd target \n");
                    execCmds.append("echo begin start server \n");
                    execCmds.append("sh start.sh \n");
                    execCmd(session,execCmds.toString(),true,object,deployState);
                    // 断开
//                    session.disconnect();
                    //
                }catch (Throwable e){
                    logger.error("deploy server error! server id={} ",deployServer.getId(),e);
                    object.put("error",e.getMessage());
                }finally {
                    latch.countDown();
                    if(session!= null){
                        session.disconnect();
                    }
                }
            });
        }
        deployState.stateInfo.put("servers",array);
        latch.await(30,TimeUnit.SECONDS);
        if(latch.getCount()>0){
            logger.error("timeout for deploy");
            deployState.stateInfo.put("error","超时返回，"+latch.getCount()+"个服务器还在部署");
        }
        Thread.sleep(1000); // 等待1秒钟，确保最新的消息返回给了client
        logger.info("end----111");
    }



    /**
     * 连接到指定的服务器
     * @return
     * @throws JSchException
     */
    public Session connect(String jschHost,String jschUserName,int jschPort,String jschPassWord) throws Throwable {

        JSch jsch = new JSch();// 创建JSch对象

        boolean result = false;
        Session session = null;
        try{

            long begin = System.currentTimeMillis();//连接前时间
            logger.info("Try to connect to jschHost = " + jschHost + ",as jschUserName = " + jschUserName + ",as jschPort =  " + jschPort);

            session = jsch.getSession(jschUserName, jschHost, jschPort);// // 根据用户名，主机ip，端口获取一个Session对象
            session.setPassword(jschPassWord); // 设置密码
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);// 为Session对象设置properties
            session.setTimeout(5000);//设置连接超时时间
            session.connect();

            logger.info("Connected successfully to jschHost = " + jschHost + ",as jschUserName = " + jschUserName + ",as jschPort =  " + jschPort);

            long end = System.currentTimeMillis();//连接后时间

            logger.info("Connected To SA Successful in {} ms", (end-begin));

            result = session.isConnected();

        }catch(Throwable e){
            logger.error(e.getMessage(), e);
            throw e;
        }finally{
            if(result){
                logger.info("connect success");
            }else{
                logger.info("connect failure");
            }
        }

        if(!session.isConnected()) {
            logger.error("获取连接失败");
            return null;

        }

        return  session;

    }

    /**
     * 上传文件
     *
     * @param directory 上传的目录,有两种写法
     *                  １、如/opt，拿到则是默认文件名
     *                  ２、/opt/文件名，则是另起一个名字
     * @param uploadFile 要上传的文件 如/opt/xxx.txt
     */
    public void upload(Session session,String directory, String uploadFile,DeployProgressSetter deployProgressSetter) throws Throwable{
        ChannelSftp chSftp = null;
        Channel channel = null;
        try {
            logger.info("Opening Channel.");
            channel = session.openChannel("sftp"); // 打开SFTP通道
            channel.connect(); // 建立SFTP通道的连接
            chSftp = (ChannelSftp) channel;

            File file = new File(uploadFile);
            long fileSize = file.length();

            /*方法一*/
            try(OutputStream out = chSftp.put(directory, new FileProgressMonitor(fileSize,deployProgressSetter), ChannelSftp.OVERWRITE)) { // 使用OVERWRITE模式{
                byte[] buff = new byte[1024 * 256]; // 设定每次传输的数据块大小为256KB
                int read;

                logger.info("Start to read input stream");
                InputStream is = new FileInputStream(uploadFile);
                do {
                    read = is.read(buff, 0, buff.length);
                    if (read > 0) {
                        out.write(buff, 0, read);
                    }
                    out.flush();
                } while (read >= 0);

                logger.info("成功上传文件至"+directory);
            }
            // chSftp.put(uploadFile, directory, new FileProgressMonitor(fileSize), ChannelSftp.OVERWRITE); //方法二
            // chSftp.put(new FileInputStream(src), dst, new FileProgressMonitor(fileSize), ChannelSftp.OVERWRITE); //方法三


        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }finally {
            chSftp.quit();

            if (channel != null) {
                logger.info("channel disconnect begin");
                channel.disconnect();
                logger.info("channel disconnect end");
            }

        }
    }

    public void execCmd(Session session,String cmd,JSONObject object,DeployState deployState) throws Throwable{
        execCmd(session,cmd,false,object,deployState);
    }
    public void execCmd(Session session,String cmd,boolean startUp,JSONObject object,DeployState deployState) throws Throwable{
        ChannelShell channel = null;
        try{
            channel = (ChannelShell) session.openChannel("shell");
            channel.connect();
            InputStream inputStream = channel.getInputStream();
            try(OutputStream outputStream = channel.getOutputStream()){
                outputStream.write(cmd.getBytes());

                String cmd6 = "exit \n\r";
                outputStream.write(cmd6.getBytes());

                outputStream.flush();
            }

            try(BufferedReader in = new BufferedReader(new InputStreamReader(inputStream))){
                String msg = null;

                boolean congratulations = false;
                boolean startupSuccess = false;

                while((msg = in.readLine())!=null){
                    if(startUp){
                        if(!congratulations && msg.contains(Server.Congratulations)){
                            congratulations = true;
                        }
                        if(!startupSuccess && msg.contains(Server.startupSuccess)){
                            startupSuccess = true;
                        }
                        if(congratulations && startupSuccess){
                            logger.info("start finish!!!!-------======");
                            object.put("st","5");
                            deployState.log.add("start finish!!!!-------======");
                            break;
                        }
                    }
                    if(msg.contains("begin start server")){
                        object.put("st","4");
                    }
                    System.out.println(msg);
                    deployState.log.add(msg);
                }
            }
        }catch (Throwable e){
            e.printStackTrace();
            throw e;
        }finally {
            if(channel!= null){
                channel.disconnect();
            }
        }
    }

    public static class DeployProgressSetter{
        Map<Integer,String> map;
        int id;
        JSONObject serverInfo;
        DeployProgressSetter(JSONObject serverInfo,Map<Integer,String> map,int id){
            this.map = map;
            this.id = id;
            this.serverInfo = serverInfo;
        }
        public void set(String msg){
            map.put(id,msg);
            this.serverInfo.put("des",msg);
        }
    }
}