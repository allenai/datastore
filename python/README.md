To use the datastore from Python, add this to your `requirements.txt`:
```
http://pip-package.dev.ai2/jcc_helper-2.0-py3-none-any.whl
https://github.com/allenai/datastore/releases/download/v1.1.0/datastore-1.0.10-cp36-cp36m-linux_x86_64.whl; python_version == '3.6' and sys_platform == 'linux'
https://github.com/allenai/datastore/releases/download/v1.1.0/datastore-1.0.10-cp36-cp36m-macosx_10_9_x86_64.whl; python_version == '3.6' and sys_platform == 'darwin'
https://github.com/allenai/datastore/releases/download/v1.1.0/datastore-1.0.10-cp37-cp37m-linux_x86_64.whl; python_version == '3.7' and sys_platform == 'linux'
https://github.com/allenai/datastore/releases/download/v1.1.0/datastore-1.0.10-cp37-cp37m-macosx_10_9_x86_64.whl; python_version == '3.7' and sys_platform == 'darwin'
```

Then, in Python, you can say this:
```
>>> import jcc_helper
>>> jcc_helper.jcc_helper.load_java_libraries()
>>> from datastore import datastore
16:32:17.836 [main] DEBUG com.amazonaws.AmazonWebServiceClient - Internal logging succesfully configured to commons logger: true
16:32:17.911 [main] DEBUG com.amazonaws.metrics.AwsSdkMetrics - Admin mbean registered under com.amazonaws.management:type=AwsSdkMetrics
16:32:18.079 [main] DEBUG c.a.internal.config.InternalConfig - Configuration override awssdk_config_override.json not found.
>>> datastore.public.file("org.allenai.quark", "arc.txt.gz", 1)
'/Users/dirkg/Library/Caches/org.allenai.datastore/public/org.allenai.quark/arc.txt-v1.gz'
```
