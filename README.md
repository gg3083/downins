https://www.qyh.me/space/java/article/downins

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
|  threadName  | 下载线程数   | 
|  location  | 下载文件存储文件夹位置   |    
|  proxyAddr  | 代理地址   |    
| proxyPort   | 代理端口   |   
| sid   | sessionid   |   