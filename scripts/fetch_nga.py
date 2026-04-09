import urllib.request
import ssl
from bs4 import BeautifulSoup
import re

url = "https://bbs.nga.cn/read.php?tid=46480916"
req = urllib.request.Request(
    url, 
    headers={
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)',
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8',
        'Accept-Encoding': 'gzip, deflate, br',
        'Cookie': 'guestJs=1;'
    }
)

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

try:
    with urllib.request.urlopen(req, context=ctx) as response:
        if response.info().get('Content-Encoding') == 'gzip':
            import gzip
            html = gzip.decompress(response.read())
        else:
            html = response.read()
            
        try:
            html = html.decode('gbk')
        except:
            html = html.decode('utf-8', 'ignore')
            
        soup = BeautifulSoup(html, 'html.parser')
        posts = soup.find_all('span', class_='postcontent')
        for i, post in enumerate(posts):
            text = post.get_text(separator='\n', strip=True)
            print(f"--- Post {i+1} ---")
            print(text)
            print("-" * 50)
            
except Exception as e:
    print("Error:", e)
