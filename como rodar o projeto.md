## workspace (docker)
```
cd rotalog-workspace
docker-compose up -d postgres
```

Obs: Se em algum momento precisar resetar o banco (por exemplo, depois de alterar scripts de migração), use ``docker-compose down -v``

## API Frotas (porta 8080):
```
cd rotalog-api-frotas
mvn spring-boot:run
```

## API Entregas (porta 3000):
```
cd rotalog-api-entregas
npm install
npm start
```

## API Notificações (porta 5000):
```
cd rotalog-api-notificacoes
dotnet restore
dotnet run
```

## Front-end
```
cd rotalog-frontend
npm install
npx nx run-many --target=serve --projects=painel-admin,rastreamento
```