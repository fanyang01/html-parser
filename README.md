# html-parser

## 目的

提取指定URL对应网页的指定信息。

## 实现方法

- 通过网络IO获取网页输入流，作为Tokenizer的输入；
- Tokenizer输出符号流，作为Parser的输入；
- Parser将符号流解析为一棵HTML树；
- 选择器根据输入构造匹配模式，遍历HTML树节点，记录遍历路径，与模式进行匹配。

## 代码要点

+ Tokenizer
  - 对`<!DOCTYPE html>`和`<!-- COMMIT -->`类标签跳过；
  - 区别`<html>`(start tag)、`</html>`(end tag)和`<br/> <img>`(self-closing tag)
  - 对`<script>`和`<style>`标签特殊处理，尝试找到其闭合标签。

+ Parser
  - 对于start tag将当前节点压栈，树沿child方向生长；
  - 对于end tag将弹栈节点作为当前节点，树沿sibling方向生长。

+ Selector
  - 选择器输入类似`body div.detail`，仅支持在最后一个标签选择器上设置id选择器和class选择器。
  - 遍历路径记录为类似`html>body>div`的格式。对于选择器输入`html div`，构建正则表达式`(\w+>)*html>(\w+>)*div`，匹配路径成功后再检查id和class是否匹配。

+ 其他
  - 字符集：部分中文站点采用GBK编码，可在InputStreamReader构造方法中设置。
  - 重定向。
  - HTML转义。

## 使用

程序读取任务列表文件，将提取到的文本输出至标准输出，可使用shell重定向功能将其重定向至文件。任务列表文件格式如下：

    # 以#开头的行为注释
    # 每行3列，格式如下
	# 网址 字符集 "选择器"
	http://www.zju.edu.cn GBK "span"
	https://baidu.com * "a.mnav"

编译后运行：

    java crawler.Main urls.txt

