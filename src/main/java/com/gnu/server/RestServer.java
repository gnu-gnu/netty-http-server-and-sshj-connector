package com.gnu.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.github.jknack.handlebars.Context;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import com.github.jknack.handlebars.Template;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;

public class RestServer {
	private String address = "127.0.0.1";
	private int port = 8888;
	private final int BOSS_THREAD_COUNT = 1;
	private final int WORKER_THREAD_COUNT = 10;
	private static final HttpDataFactory factory = new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE);

	public RestServer() {
		super();
	}

	public RestServer(int port) {
		this.port = port;
	}

	public RestServer(String address, int port) {
		this.address = address;
		this.port = port;

	}

	public void start() {
		EventLoopGroup bossGroup = new NioEventLoopGroup(BOSS_THREAD_COUNT);
		EventLoopGroup workerGroup = new NioEventLoopGroup(WORKER_THREAD_COUNT);
		ChannelFuture channelFuture = null;
		try {
			ServerBootstrap server = new ServerBootstrap();
			server.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
					.handler(new LoggingHandler(LogLevel.INFO)).childHandler(new ChannelInitializer<SocketChannel>() {
						@Override
						protected void initChannel(SocketChannel ch) throws Exception {
							ChannelPipeline pipe = ch.pipeline();
							pipe
							.addLast(new HttpRequestDecoder())
							.addLast(new HttpObjectAggregator(1024 * 128))
							.addLast(new HttpResponseEncoder())
							.addLast(new HttpContentCompressor())
							.addLast(new SimpleChannelInboundHandler<FullHttpMessage>() {

										@Override
										protected void channelRead0(ChannelHandlerContext ctx, FullHttpMessage msg)
												throws Exception {
											HttpRequest request = null;
											Map<String, List<String>> requestParams = null;
											
											if(msg instanceof HttpRequest){
												request = (HttpRequest)msg;
												System.out.println("request ------------------------");
												System.out.println("is 100 : "+HttpUtil.is100ContinueExpected(request));
												request.headers().remove("content-length");
												System.out.println(request.toString());
												HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(factory, request);
												QueryStringDecoder queryStringDecoder = new QueryStringDecoder(request.uri());
												requestParams = queryStringDecoder.parameters();
												decoder.destroy();
												
											}
											
											if(msg instanceof HttpContent) {
												System.out.println(">>>>> content");
												HttpContent content = (HttpContent)msg;
												if(msg instanceof LastHttpContent) {
													LastHttpContent lastContent = (LastHttpContent)msg;
													System.out.println(">>>>> last content : "+lastContent.decoderResult().isSuccess());
													String name = Optional.of(requestParams.get("name").get(0)).orElse("Friend!");
													Handlebars hbs = new Handlebars();
													Template template = hbs.compile("page/HelloWorld");
													Context context = Context.newContext("this").combine("name", name);
													String html = template.apply(context);
													
													
													FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.copiedBuffer(html, CharsetUtil.UTF_8));
													AsciiString texthtml = new AsciiString("text/html; charset=UTF-8", CharsetUtil.UTF_8);
													response.headers().set(HttpHeaderNames.CONTENT_TYPE, texthtml);
													if(HttpUtil.isKeepAlive(request)) {
														System.out.println("keep-alive response");
														response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
														response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
														System.out.println("response ---------------------");
														System.out.println(response.toString());
														ChannelFuture cf = ctx.write(response);
														if (!cf.isSuccess()) {
														    System.out.println("Send failed: " + cf.cause());
														}
													} else {
														System.out.println(">>>>> none keep-alive response");
														ctx.write(response);
														ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
													}
													request = null;
												}
												
											}
										}

										@Override
										public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
											// TODO Auto-generated method stub
											System.out.println(">>>>> complete");
											ctx.flush();
										}

										@Override
										public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
												throws Exception {
											System.out.println(">>>>> exception");
											FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR, Unpooled.copiedBuffer("exception", CharsetUtil.UTF_8));
											cause.printStackTrace();
											ctx.writeAndFlush(response);
										}
										
										
										
									});
						}
					});

	Channel channel = server.bind(address, port).sync()
			.channel();channelFuture=channel.closeFuture();channelFuture.sync();}catch(
	InterruptedException e)
	{
		e.printStackTrace();
	}
}

}
