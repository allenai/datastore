from setuptools import setup
from codecs import open
from os import path

here = path.abspath(path.dirname(__file__))

# Get the long description from the README file
with open(path.join(here, 'README.md'), encoding='utf-8') as f:
    long_description = f.read()

setup(
    name='datastore',
    version='3.0.0',
    description='Store for immutable objects in S3',
    long_description=long_description,
    url='https://github.com/allenai/datastore',
    author='Dirk Groeneveld',
    author_email='dirkg@allenai.org',
    classifiers = [
        'Development Status :: 5 - Production/Stable',
        'Intended Audience :: Developers',
        'License :: OSI Approved :: Apache License',
        'Programming Language :: Python :: 3'
    ],
    py_modules=['datastore'],
    test_suite="datastore_test",
    install_requires=['botocore', 'boto3'],
    project_urls={
        'Bug Reports': 'https://github.com/allenai/datastore/issues',
        'Funding': 'https://allenai.org',
        'Source': 'https://github.com/allenai/datastore',
    },
    python_requires='>=3'
)
