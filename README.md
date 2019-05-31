# 使用了非官方的API，仅供娱乐使用

## 特性

1.  一行命令下载用户所有帖子的图片|视频
2.  支持下载私密账户(需要设置sessionid)
3.  支持增量下载
4.  通过web服务选择性下载

## 通过命令行下载

下载某个用户所有帖子中的图片|视频
```
java -jar /path/to/jar u username|url
```
下载某个用户所有帖子中的图片|视频，并且将`maxFileInDir`个文件组成一个文件夹
```
java -jar /path/to/jar u username|url maxFileInDir
```
下载某个帖子的全部图片|视频
```
java -jar /path/to/jar p shortcode|url
```
下载某个标签的全部图片|视频
```
java -jar /path/to/jar t tag|url maxFileInDir
```
下载某个IGTV的视频
```
java -jar /path/to/jar i shortcode|url
```
下载某个用户的全部IGTV
```
java -jar /path/to/jar c username|url maxFileInDir
```
下载某个story下的全部文件
```
java -jar /path/to/jar _s storyid|url
```
下载某个用户的全部story文件
```
java -jar /path/to/jar ss username|url
```
打开配置面板(如果支持GUI)
```
java -jar /path/to/jar s
```
直接设置(如果不支持GUI)
```
java -jar /path/to/jar s threadNum=1
```

可以将下载线程数更新为1(**其他属性不会被更新**)，键名称如下(忽略大小写)：

|  键名称  | 说明   |    
|  -  |  -  |
|  threadNum  | 下载线程数   | 
|  location  | 下载文件存储文件夹位置   |    
|  proxyAddr  | 代理地址   |    
| proxyPort   | 代理端口   |   
| sid   | sessionid   |   

## 通过web服务选择性下载
**web服务只能用于本地下载，不能对外提供服务，在此之前，请确保默认端口(21134)或者指定的端口可以被程序占用**
1. 开启web服务  
```
java -jar /path/to/jar [port]
```
2. 选择文件  
![选择文件.png](https://www.qyh.me/image/github/QQ截图20190531234422.png/600)

3. 点击下载  
![下载.png](https://www.qyh.me/image/github/QQ截图20190531234439.png/600)

## 通过程序下载
**程序只封装了图片|视频的访问地址，类型等用于下载的最基本信息，无法获取点赞数、访问数等其他信息**
```java
// 设置ss代理、保存地址并保存
Configure.get().getConfig().setProxyAddr("127.0.0.1").setProxyPort(1080).setLocation("d:/downins3").store();

CloseableHttpClient client = Https.newHttpClient();
InsParser ip = new InsParser(false, client);

// 获取帖子的图片|视频访问地址
PostInfo postInfo = ip.parsePost("帖子code");

// 获取IGTV的访问地址
PostInfo igtvInfo = ip.parseIGTV("IGTV_code");

//根据id查询story中所有文件的访问地址
Map<String,List<Url>> map = ip.parseStory(String ... ids);

UserParser up = ip.newUserParser("用户名", false);
// 分页浏览用户的全部帖子信息
int pageSize = 12;
UserPagingResult upr = up.paging("", pageSize);
while (upr.isHasNextPage()) {
    try {
        Thread.sleep(1000);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }
    upr = up.paging(upr.getEndCursor(), pageSize);
}

//浏览用户的全部story(不包含story下面具体文件的访问地址)
List<Story> stories = up.stories();

TagParser tp = ip.newTagParser("陈钰琪可爱", false);
// 分页浏览标签下的所有帖子
TagPagingResult tpr = tp.paging("", pageSize);
while (tpr.isHasNextPage()) {
    try {
        Thread.sleep(1000);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }
    tpr = tp.paging(tpr.getEndCursor(), pageSize);
}

// 浏览标签的热门帖子
List<PagingItem> items = tp.tops();

ChannelParser cp = ip.newChannelParser("用户名", false);
// 分页浏览用户的IGTV
ChannelPagingResult cpr = cp.paging("", pageSize);
while (cpr.isHasNextPage()) {
    try {
        Thread.sleep(1000);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }
    cpr = cp.paging(cpr.getEndCursor(), pageSize);
}

//下载文件
Https.download(client, HttpRequestBase req, Path dest, DownloadProgressNotify notify, Path temp);
```
