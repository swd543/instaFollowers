const express = require('express');
const bodyParser = require('body-parser')
var http = require('https');
const path = require('path');
const app = express();
app.use(express.static(path.join(__dirname, 'build')));

app.get('/ping', function (req, res) {
 return res.send('pong');
});

app.get('/', function (req, res) {
  res.sendFile(path.join(__dirname, 'build', 'index.html'));
});

app.get('/note', function (req, res) {
  const url = "https://scribz.net/Note/LoadNote";

  const data = JSON.stringify({id: 1371489})
  
  const options = {
    hostname: 'scribz.net',
    port: 443,
    path: '/Note/LoadNote',
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Content-Length': data.length,
      Cookie:"__utmc=70802834; __utma=70802834.1117066590.1582739318.1582739318.1582739369.2; __utmz=70802834.1582739369.2.2.utmcsr=google|utmccn=(organic)|utmcmd=organic|utmctr=(not%20provided); ARRAffinity=a991d1b01a4ada1061a8286607d6d71662d942e1855f5196813585a6fe5d6ec4; SCRIBZ_USER=N3yghWaUIzMujL6vs53/hnDMKU54of07l1XdfCYcrS01HqzuZcMedj5jkMSOU9/hs5pzNV8xfPSNcrfmKuWVZ26WXjonffVs6lmcF1o73x/uBSjFDM79RnaRXrzrriYnJZ04mcFLvSIr/TD4mF+6g2rJrGpRtCgKNI0IBpTBu68yBFf6WXOw+lXDW64AqpyYl8nonMJfcOyCtGT1XQKLUyc6Nvr68c/hFnaKZJGS6ZAbjwTbKRP++osTjESPSm/0mRdXAlz449FZTCD/sB2qAcz8Ia4NdAs8Q3gLQCLN87PzQnN4ghBDuyfhp3KMoyfBXxTpwsjx+lBgnUjCEG6nrzmLE8i9++VMbiHw2e4wZ7XXf8Jc6MbKnpzUVAf12V+Fh3UFja8wThQbNjOb80kFwivVQ88pINqTg6NmkfgMLeU="
    }
  }

  const reqs = http.request(options, (resx) => {
    console.log(`statusCode: ${resx.statusCode}`)
  
    resx.on('data', (d) => {
      process.stdout.write(d);
      res.send('potty');
    })
  })
  reqs.on('error', (error) => {
    console.error(error)
  })
  
  reqs.write(data)
  reqs.end()
  
  // res.send('asd');
});

app.listen(process.env.PORT || 8080);