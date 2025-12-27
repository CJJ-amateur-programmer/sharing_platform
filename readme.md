### artefact

此目录为测试运行目录。编译出jar后，将jar置于此目录下，使用

```bash
java -jar sharing_platform-x.x.jar
```

即可运行，查看效果。

目录说明：
*   **`./static`**：存放主界面和登陆界面。
*   **`./log`**：存放日志文件`runtime.log`。
*   **`./config.json`**：存放配置。`JWT_SECRET`为JWT密钥，生产环境中务必修改；`dateAttrNames`表示数据库中的需要将4位/6位/8位数字转成日期的属性；`numAttrNames`表示数据库中的数值型属性；`selectColumns`表示SELECT语句返回的列。

网页中有详细的搜索使用说明（以及一个小彩蛋?）。