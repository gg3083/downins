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

## 项目代码

完整的项目代码可以在 [downins](https://github.com/mhlx/downins) 这里看到，或者
点击 [downins.jar](https://github.com/mhlx/downins/releases/download/1.2.0/downins.jar) 直接下载 jar包。

## 修改程序配置文件

如果不支持GUI，可以通过在命令行中指定键值的方式修改，例如
```
java -jar /path/to/downins.jar s threadNum=1
```
可以将下载线程数更新为1(**其他属性不会被更新**)，键名称如下(忽略大小写)：
|  键名称  | 说明   |    
|  -  |  -  |
|  threadName  | 下载线程数   | 
|  location  | 下载文件存储文件夹位置   |    
|  proxyAddr  | 代理地址   |    
| proxyPort   | 代理端口   |   
| sid   | sessionid   |    

如果需要删除某些属性，直接留空即可，例如
```
java -jar /path/to/downins.jar s proxyAddr=
```
将会删除代理地址

## 关于私密账户的下载
私密账户需要用户关注他们后才可以浏览他们的帖子，比如 https://www.instagram.com/foodys  这样的话就首先需要模拟登录，但由于Instagram会在用户登录时通过一些手段判断是否是人工操作(怪不得Instagram没有验证码),这会导致无法模拟登录，所以退而求其次，手动填写sessionid(同样保存在配置文件中)，在pc浏览器下，获取这个cookie应该非常容易。


## 测试用户 
https://www.instagram.com/1d.legendary.updates/ 一个很有代表性的用户，拥有高达12w+的帖子(截止至2018-08-20)，而且帖子内容非常丰富，有图片，有视频，也有相当多的 图片视频集合(Instagram叫做`GraphSidecar`)，由于帖子太多，只下载了2397个帖子，下载总容量3.75GB，下载文件失败一个，最大成功4243个(由于直接关闭了进程，一些文件还在下载中)，下载并发线程数15，用的搬瓦工的openvpn，100M宽带。

https://www.instagram.com/snake___pit/ 下载完毕所有文件(共有4053个帖子，截至到2018-08-20)，都是图片文件，耗时335秒，下载总容量464M，下载文件4108个 ，并发线程数5，用的ExpressVPN，连的手机4G热点。

https://www.instagram.com/vidz/ 下载完毕所有文件(共有1136个帖子**官方标注1138个**，截至到2018-08-22)，基本都是视频文件，耗时5579秒，下载总容量4.9G，下载文件1188个 ，并发线程数5，用的ExpressVPN,10M带宽。


## 关于实际帖子数和官网显示的不一致
测试过程中发现，有些用户下载下来的帖子数和官网标注的不一致，例如 [vidz](https://www.instagram.com/vidz/)，官网标注为1120(截止到2018-08-20)但下载下来只有1118个帖子，后面到app中进行了比对，发现app确实只能显示1118个帖子

## 关于多个下载进程
应该始终保持**一个**下载进程，多个下载进程非常容易导致请求返回429状态码(rate limit)，一旦检测到429状态码，主进程将会休眠30s(以后每次增加30s)后再次发送请求，直到返回200状态码为止。

## 更新日志
2018-08-22   
1.  支持用户增量下载
2.  努力确保在进程异常关闭后再次下载时不会丢失文件(仍然会进行查询匹配)

2018-08-23
1. 修复了web服务下载文件时进度条进度始终为0的bug
2. 优化了下载GraphSidecar的性能

2018-08-29
1. posts.txt不再记录帖子的shortcode，而是记录帖子的ID，因为shortcode可能会发生变化(例如账户在私密-非私密之间转化)

2018-09-12
1. 支持设置代理地址和端口，以支持ss

2018-09-19
1. 新增配置文件修改命令行

2018-10-29
1. 修复了在没有下一页时，当前页面数据获取失败的bug

2018-12-27
1. 修复了保存的文件后缀名为com的bug

2019-03-26
1. 重新编译，用于支持JDK8

2019-05-07
1. 支持按标签下载(实验)
2. 修复了开启web服务后，下载用户帖子时，勾选全部下载但只能下载最后一次加载的帖子的bug

2019-05-16
1. 修复了按照用户|帖子下载时设置查询参数失败的bug
2. 修复了web取消下载后任务仍然进行的bug
3. 修复了设置sessionid后没有生效的bug

2019-05-24
1.  新增channel|IGTV的下载

2019-05-31
1.  新增story的下载

## 配置SS
1.配置SS允许来自局域网的链接  
[![QQ截图20190326192533.png](https://www.qyh.me/image/article/java/QQ截图20190326192533.png/600)](https://www.qyh.me/image/article/java/QQ截图20190326192533.png/900)

2.配置代理端口  
[![QQ截图20190326192717.png](https://www.qyh.me/image/article/java/QQ截图20190326192717.png/600)](https://www.qyh.me/image/article/java/QQ截图20190326192717.png/900)

3.1
如果系统支持GUI，运行命令，填写代理地址和端口
```
java -jar /path/to/downins.jar s
```

[![QQ截图20190326193054.png](https://www.qyh.me/image/article/java/QQ截图20190326193054.png/600)](https://www.qyh.me/image/article/java/QQ截图20190326193054.png/900)

如果系统不支持GUI，运行如下命令即可：
```
java -jar /path/to/downins.jar s proxyAddr=127.0.0.1 proxyPort=1080
```
