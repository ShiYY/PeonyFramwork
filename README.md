# PeonyFramwork
一个优秀的java后端框架，上手简单，使用灵活方便。可以应用于网络游戏，网络应用的服务端开发。目前已经应用于10余款游戏的服务端开发，并正在被更过的游戏使用<br>

[最大限度的减少和业务逻辑无关的工作]

# 启动框架

1、用idea，Open->选择根目录的build.gradle文件->把项目作为gradle项目打开 <br>
2、找到src->main->resources->mmserver.properties文件，修改里面的数据库配置，包括数据库连接，用户名和密码。（不用创建表）<br>
3、找到启动类com.peony.engine.framework.server.Server，运行。<br>
成功启动的标志为：<br>
-------------------------------------------------------------<br>
|                      Congratulations                      |<br>
|              server(ID:  1) startup success!              |<br>
-------------------------------------------------------------<br>

# 基本使用
以下以框架提供的消息入口WebSocketEntrance和json协议为例，实现一个简单的背包功能<br>
**下面代码均在工程中，如果想按步骤重复，请先删除工程中已经存在的，否则会产生冲突**
#### 一. 定义一个背包存储类DBEntity
```$xslt
@DBEntity(tableName = "bag",pks = {"uid","itemId"})
public class BagItem implements Serializable {
    private String uid; // 玩家id
    private int itemId; // 物品id
    private int num; // 物品数量

    // get set 方法
}
```
* 注解@DBEntity声明一个类为背包类，对应数据库中一张表，其参数tableName对应表名，
pks对应表的主键，可以为多个。
* @DBEntity声明的类必须继承Serializable接口，并对参数实现get set方法
* 表不用手动创建，系统启动时会自动在数据库中创建，大多数的修改也会进行同步，所以不要声明不需要存储数据库的字段
#### 二. 定义一个背包处理服务类Service
```$xslt
@Service
public class BagService {

    private DataService dataService;
    /**
     * 协议，获取背包列表
     */
    @Request(opcode = Cmd.BagInfo)
    public JSONObject BagInfo(JSONObject req, Session session) {
        List<BagItem> bagItemList = dataService.selectList(BagItem.class,"uid=?",session.getAccountId());
        JSONObject ret = new JSONObject();
        JSONArray array = new JSONArray();
        for(BagItem bagItem : bagItemList){
            array.add(bagItem.toJson());
        }
        ret.put("bagItems",array);
        return ret;
    }

    /**
     * 添加物品到背包
     */
    @Tx
    public int addItem(String uid,int itemId,int num){
        BagItem bagItem =  dataService.selectObject(BagItem.class,"uid=? and itemId=?",uid,itemId);
        if(bagItem == null){
            bagItem = new BagItem();
            bagItem.setUid(uid);
            bagItem.setItemId(itemId);
            bagItem.setNum(num);
            dataService.insert(bagItem);
        }else{
            bagItem.setNum(bagItem.getNum()+num);
            dataService.update(bagItem);
        }
        return bagItem.getNum();
    }

    /**
     * 从背包中删除物品
     */
    @Tx
    public int decItem(String uid,int itemId,int num){
        BagItem bagItem =  dataService.selectObject(BagItem.class,"uid=? and itemId=?",uid,itemId);
        if(bagItem == null){
            throw new ToClientException(SysConstantDefine.InvalidParam,"item is not enough1,itemId={}",itemId);
        }else{
            if(bagItem.getNum()<num){
                throw new ToClientException(SysConstantDefine.InvalidParam,"item is not enough2,itemId={}",itemId);
            }
            bagItem.setNum(bagItem.getNum()-num);
            dataService.update(bagItem);
        }
        return bagItem.getNum();
    }
}
```
* 注解@Service声明一个类为一个服务类
* 声明了数据服务类DataService，该类中提供了对数据操作的所有接口。
引用其他服务类时，声明即可使用，系统启动时会进行依赖注入
* @Request声明一个方法为处理客户端协议的方法，其中opcode为协议号(整型)，因为使用的是json协议，
参数类型必须为JSONObject和Session，返回值类型为JSONObject。其中参数JSONObject为客户端发送过来的消息
Session中有玩家基本信息，包括玩家账号`session.getAccountId()`
* 代码`List<BagItem> bagItemList = dataService.selectList(BagItem.class,"uid=?",session.getAccountId());`
可以获取玩家的背包列表，而`BagItem bagItem =  dataService.selectObject(BagItem.class,"uid=? and itemId=?",uid,itemId);`
则可以获取背包中具体某个物品的信息
* 注解@Tx可以确保整个方法的执行在服务层事务中，确保业务失败的数据回滚和并发情况下的数据一致问题
#### 三. 前端调用
* 打开websocket在线工具：http://www.blue-zero.com/WebSocket/
* 在地址输入框中输入：ws://localhost:8002/websocket
* 在发送消息框中输入登录消息如下：
```$xslt
{
    "id": "102", 
    "data": {
        "accountId": "test1"
    }
}
```
其中，id为登录消息协议号，accountId为登录的账号<br>
点击发送，可得到登录成功的返回：
```$xslt
{
    "data": {
        "accountId": "test2", 
        "newUser": 1, 
        "serverTime": 1540206073929
    }, 
    "id": 102
}
```
其中newUser标识为新用户，serverTime为服务器时间，id和accountId同上
* 登录完成后，就可以进行背包信息的获取了，发送消息
```$xslt
{
    "id": "20011", 
    "data": {
        
    }
}
```
可收到消息：
```$xslt
{
    "data": {
        "bagItems": [ ]
    }, 
    "id": 20011
}
```
由于背包中为空，所以bagItems返回一个空数据，玩家可自行实现一个添加物品到背包的协议进行测试