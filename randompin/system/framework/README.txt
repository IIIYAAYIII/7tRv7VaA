# 此目录用于存放编译后的Xposed模块DEX文件
# 
# 如果你有编译好的randompin.dex文件，请将其放置在此目录
# 
# 编译方法：
# 1. cd xposed_src
# 2. javac -source 1.8 -target 1.8 -d ../build *.java
# 3. d8 --output . ../build/*.class
# 4. 将生成的classes.dex重命名为randompin.dex并放入此目录
#
# 或者运行根目录下的build.sh/build.bat脚本自动编译
