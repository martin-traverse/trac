/*
 * Copyright 2021 Accenture Global Solutions Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

const {http, https} = require('follow-redirects');
const fs = require('fs');
const path = require('path');
const childProcess = require('child_process');
const AdmZip = require('adm-zip');

const packages = {
    'trac/metadata': '../../trac-metadata/src/main/proto',
    'trac/api': '../../trac-services/src/main/proto'
};

const PROTOBUF_VERSION = '3.15.7';
const GRPC_WEB_VERSION = '1.2.1'
const platform = 'osx';
const platformGrpcWeb = 'darwin';  // platform names are different from protoc
const arch = 'x86_64';

const DOWNLOAD_DIR = './build/download';
const PROTOC_DIR = './build/protoc';
const PROTO_DIR = './build/proto';
const DIST_DIR = './dist'

const GRPC_WEB_MODE = 'grpcweb';  // grpcweb | grpcwebtext

const protocPackageUrl =
    "https://github.com/protocolbuffers/protobuf/releases/download" +
    `/v${PROTOBUF_VERSION}/protoc-${PROTOBUF_VERSION}-${platform}-${arch}.zip`

const grpcWebPackageUrl =
    "https://github.com/grpc/grpc-web/releases/download" +
    `/${GRPC_WEB_VERSION}/protoc-gen-grpc-web-${GRPC_WEB_VERSION}-${platformGrpcWeb}-${arch}`


function getPackage(url, path) {

    console.log("Download: " + url);

    return new Promise((resolve, reject) => {

        const stream = fs.createWriteStream(path);
        const scheme = url.startsWith("https") ? https : http;
        let fileInfo = null;

        const request = scheme.get(url, (response) =>  {

            if (response.statusCode !== 200) {
                reject(new Error(`Failed to get '${url}' (${response.statusCode})`));
                return;
            }

            fileInfo = {
                mime: response.headers['content-type'],
                size: parseInt(response.headers['content-length'], 10),
            };

            response.pipe(stream);
        });

        stream.on('finish', () => resolve(fileInfo));

        stream.on('error', err => {
            fs.unlink(path, () => reject(err));
        });

        request.on('error', err => {
            fs.unlink(path, () => reject(err));
        });

        request.end();
    });
}

function unzip(zipFile, dest) {

    console.log(`Unzip: ${zipFile} -> ${dest}`);

    return new Promise((resolve, reject) => {

        const zip = new AdmZip(zipFile, {});

        zip.extractAllToAsync(dest, true, (err, result) => {

            if (err)
                reject(err);
            else
                resolve(result);
        });
    });
}

function download() {

    fs.mkdirSync(DOWNLOAD_DIR, {recursive: true});
    fs.mkdirSync(PROTOC_DIR, {recursive: true});
    fs.mkdirSync(`${PROTOC_DIR}/bin`, {recursive: true});

    getPackage(protocPackageUrl, `${DOWNLOAD_DIR}/protoc.zip`)
        .then(result => console.log("ok", result))
        .then(_ => unzip(`${DOWNLOAD_DIR}/protoc.zip`, PROTOC_DIR))
        .then(_ => console.log("ok"))
        .then(_ => fs.chmodSync(`${PROTOC_DIR}/bin/protoc`, "755"))
        .catch(err => console.log("error", err));

    getPackage(grpcWebPackageUrl, `${DOWNLOAD_DIR}/protoc-gen-grpc-web`)
        .then(result => console.log("ok", result))
        .then(_ => fs.copyFileSync(`${DOWNLOAD_DIR}/protoc-gen-grpc-web`, `${PROTOC_DIR}/bin/protoc-gen-grpc-web`))
        .then(_ => fs.chmodSync(`${PROTOC_DIR}/bin/protoc-gen-grpc-web`, "755"))
        .catch(err => console.log("error", err));
}


function copyProto() {

    for (const [pkg, pkgDir] of Object.entries(packages)) {

        const srcDir = path.join(pkgDir, pkg);
        const dstDir = path.join(PROTO_DIR, pkg);

        fs.mkdirSync(dstDir, {recursive: true});

        fs.readdirSync(srcDir).forEach(file => {

            const srcProto = path.join(srcDir, file);
            const dstProto = path.join(dstDir, file);

            fs.copyFileSync(srcProto, dstProto);
        })
    }
}


function sh(cmd) {

    return new Promise(function (resolve, reject) {

        childProcess.exec(cmd, (err, stdout, stderr) => {
            if (err) {
                reject(err);
            } else {
                resolve({ stdout, stderr });
            }
        });
    });
}



function generate() {

    fs.mkdirSync(DIST_DIR, {recursive: true});

    const commands = [];

    for (const pkg of Object.keys(packages)) {

        const pkgDir = path.join(PROTO_DIR, pkg);

        fs.readdirSync(pkgDir).forEach(file => {

            const srcProto = path.join(pkgDir, file);
            const jsCmd = `protoc -I=${PROTO_DIR} ${srcProto} --js_out=import_style=commonjs,binary:${DIST_DIR}`;
            const grpcWebCmd = `protoc -I=${PROTO_DIR} ${srcProto} --grpc-web_out=import_style=commonjs+dts,mode=${GRPC_WEB_MODE}:${DIST_DIR}`;

            commands.push(jsCmd);
            commands.push(grpcWebCmd);
        })
    }

    Promise.all(commands.map(cmd => sh(cmd)));
}


// download();
copyProto();
generate();
