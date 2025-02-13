# Android mDNSResponder [![Circle CI](https://circleci.com/gh/andriydruk/RxDNSSD.svg?style=shield&circle-token=5f0cb1ee907a20bdb08aa4b073b5690afbaaabe1)](https://circleci.com/gh/andriydruk/RxDNSSD) [![Download](https://img.shields.io/maven-central/v/com.github.andriydruk/dnssd?label=DNSSD)](https://search.maven.org/artifact/com.github.andriydruk/dnssd) [![Download](https://img.shields.io/maven-central/v/com.github.andriydruk/rxdnssd?label=RxDNSSD)](https://search.maven.org/artifact/com.github.andriydruk/rxdnssd) [![Download](https://img.shields.io/maven-central/v/com.github.andriydruk/rx2dnssd?label=Rx2DNSSD) ](https://search.maven.org/artifact/com.github.andriydruk/rx2dnssd)


## Why I created this library?
My [explanation](http://andriydruk.com/post/mdnsresponder/) about why jmDNS, Android NSD Services and Google Nearby API are not good enough, and why I maintain this library.

## Hierarchy

There are two version of mDNSReposder. 

Bindable version:

```
                                   +--------------------+       +--------------------+
                                   |      RxDNSSD       |       |       Rx2DNSSD     |
                                   +--------------------+       +--------------------+
                                           |                            |
                                           |   +--------------------+   |
                                            -->| Android Java DNSSD |<--
                                               +--------------------+
                                               |  Apple Java DNSSD  |
                 +------------------+          +--------------------+
                 |    daemon.c      |<-------->|     mDNS Client    |
                 +------------------+          +--------------------+
                 |    mDNS Core     |
                 +------------------+
                 | Platform Support |
                 +------------------+
                    System process                Your Android app

```

Embedded version:

```
                     +--------------------+       +--------------------+
                     |      RxDNSSD       |       |       Rx2DNSSD     |
                     +--------------------+       +--------------------+
                                |                            |
                                |   +--------------------+   |
                                 -->| Android Java DNSSD |<--
                                    +--------------------+
                                    |   Apple Java DNSSD |    
                                    +--------------------+
                                    |    mDNS Client     |
                                    +--------------------+
                                    | Embedded mDNS Core |
                                    +--------------------+
                                    | Platform Support   |
                                    +--------------------+
                                      Your Android app

```

## Binaries on MavenCentral

DNSSD library:

```groovy
compile 'com.github.andriydruk:dnssd:0.9.17'
```

RxDNSSD library:

```groovy
compile 'com.github.andriydruk:rxdnssd:0.9.17'
```

Rx2DNSSD library:

```
compile 'com.github.andriydruk:rx2dnssd:0.9.17'
```

* It's built with Andorid NDK 21 for all platforms (1.7 MB). If you prefer another NDK version or subset of platforms, please build it from source with command:

```groovy
./gradlew clean build
```

## How to use

### DNSSD

Dnssd library provides two implementations of DNSSD interface: 

DNSSDBindable is an implementation of DNSSD with system's daemon. Use it for Android project with min API higher than 4.1 for an economy of battery consumption (Also some Samsung devices can don't work with this implementation).

**[Update] Since `targetSDK = 31` (Android 12) system's deamon was deprecated by Google. Consider switching to an Embedded version or some other solution.**

```
DNSSD dnssd = new DNSSDBindable(context); 
```

DNSSDEmbedded is an implementation of RxDnssd with embedded DNS-SD core. Can be used for any Android device with min API higher than Android 4.0.

```
DNSSD dnssd = new DNSSDEmbedded(); 
```

##### Register service
```java
try {
	registerService = dnssd.register("service_name", "_rxdnssd._tcp", 123,  
   		new RegisterListener() {

			@Override
			public void serviceRegistered(DNSSDRegistration registration, int flags, 
				String serviceName, String regType, String domain) {
				Log.i("TAG", "Register successfully ");
			}

			@Override
         	public void operationFailed(DNSSDService service, int errorCode) {
				Log.e("TAG", "error " + errorCode);
        	}
   		});
} catch (DNSSDException e) {
	Log.e("TAG", "error", e);
}
```

##### Browse services example
```java
try {
	browseService = dnssd.browse("_rxdnssd._tcp", new BrowseListener() {
                
 		@Override
		public void serviceFound(DNSSDService browser, int flags, int ifIndex, 
			final String serviceName, String regType, String domain) {
			Log.i("TAG", "Found " + serviceName);
		}

		@Override
		public void serviceLost(DNSSDService browser, int flags, int ifIndex, 
			String serviceName, String regType, String domain) {
			Log.i("TAG", "Lost " + serviceName);
		}

		@Override
		public void operationFailed(DNSSDService service, int errorCode) {
			Log.e("TAG", "error: " + errorCode);
		}        
	});
} catch (DNSSDException e) {
	Log.e("TAG", "error", e);
}
```

You can find more samples in app inside this repository.

### RxDNSSD

- RxDnssdBindable
```
RxDnssd rxdnssd = new RxDnssdBindable(context); 
```
- RxDnssdEmbedded
```
RxDnssd rxdnssd = new RxDnssdEmbedded(); 
```

##### Register service
```java
BonjourService bs = new BonjourService.Builder(0, 0, Build.DEVICE, "_rxdnssd._tcp", null).port(123).build();
Subscription subscription = rxdnssd.register(bonjourService)
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe(service -> {
      		updateUi();
      }, throwable -> {
        	Log.e("DNSSD", "Error: ", throwable);
      });
```

##### Browse services example
```java
Subscription subscription = rxDnssd.browse("_http._tcp", "local.")
	.compose(rxDnssd.resolve())
    .compose(rxDnssd.queryRecords())
    .subscribeOn(Schedulers.io())
    .observeOn(AndroidSchedulers.mainThread())
    .subscribe(new Action1<BonjourService>() {
    	@Override
        public void call(BonjourService bonjourService) {
        	Log.d("TAG", bonjourService.toString());
        }
    }, new Action1<Throwable>() {
        @Override
        public void call(Throwable throwable) {
        	Log.e("TAG", "error", throwable);
        }
	});
```

### Rx2DNSSD

- Rx2DnssdBindable
```
Rx2Dnssd rxdnssd = new Rx2DnssdBindable(context); 
```
- Rx2DnssdEmbedded
```
Rx2Dnssd rxdnssd = new Rx2DnssdEmbedded(); 
```

##### Register service
```java
BonjourService bs = new BonjourService.Builder(0, 0, Build.DEVICE, "_rxdnssd._tcp", null).port(123).build();
registerDisposable = rxDnssd.register(bs)
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(bonjourService -> {
            Log.i("TAG", "Register successfully " + bonjourService.toString());
        }, throwable -> {
            Log.e("TAG", "error", throwable);
        });
```

##### Browse services example
```java
browseDisposable = rxDnssd.browse("_http._tcp", "local.")
        .compose(rxDnssd.resolve())
        .compose(rxDnssd.queryRecords())
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(bonjourService -> {
            Log.d("TAG", bonjourService.toString());
            if (bonjourService.isLost()) {
                mServiceAdapter.remove(bonjourService);
            } else {
                mServiceAdapter.add(bonjourService);
            }
        }, throwable -> Log.e("TAG", "error", throwable));
```

License
-------
	Copyright (C) 2022 Andriy Druk

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
