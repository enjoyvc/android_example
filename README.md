# Enjoyvc WebRTC Cloud
# Android example

*使用chrome或者Safari 打开 url，https://webrtc.enjoyvc.com/cloud/
android studio import aarproject，build and run!

*在org.appsport.apprtc 包下，新增加 enjoyvc 包。
*enjoyvc包下有三个类，
EnjoyvcMedia		封装原来例子的PeerConnectionClient
EnjoyvcRTC		enjoyvc webrtc云平台的通信部分，发布视频、订阅视频等等。
EnjoyvcRTCClient	实现原来例子的 AppRTCClient信令接口。

*CallActivity类
CallActivity里面， EnjoyvcRTCClient替换原来的WebSocketRTCClient
appRtcClient = new EnjoyvcRTCClient(this);
相当于信令模块。

增加两个媒体处理的类，一个是发布视频， 另一个是订阅视频。
private EnjoyvcMedia enjoyvcPublish;
private EnjoyvcMedia enjoyvcSubscribe;


*处理流程
请查看 doc/android_example.txt

