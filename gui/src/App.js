import React, { useEffect, useState } from 'react';
import logo from './logo.svg';
import './App.css';

function App() {
  const [authenticated, setAuthenticated]=useState(false);

  useEffect(()=>{
    const getNoteByName=(name)=>()=>{
      fetch('Note/LoadStubs', {
        method:'POST',
        credentials:'include',
        headers:{'Content-Type': 'application/json'},
        body: JSON.stringify({sort: 0, filter: null})
      })
      .then(r=>r.json())
      .then(r=>r.Stubs.find(p=>p.T===name))
      .then(r=>r.ID)
      .then(r=>getNoteById(r))
      .catch(console.log)
    }
  
    const getNoteById=(id)=>{
      fetch('Note/LoadNote', {
        method:'POST',
        credentials:'include',
        headers:{'Content-Type': 'application/json'},
        body: JSON.stringify({id})
      })
      .then(r=>r.json())
      .then(r=>r.Note.Body)
      .then(JSON.parse)
      .then(r=>{
        setRawData(r)
        console.log(r)
      })
      .catch(console.log)
    }

    fetch('login',{
      method:'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({UserName:'swd543', Password:'helloscribz!22213',RememberMe:true})
    })
    .then(r=>r.type!=="opaque" && r.status===200 && setAuthenticated(true))
    .then(getNoteByName("Instagram"))
    .catch(console.log)
  }, [])

  const [rawData, setRawData]=useState()

  return (
    <div className="App">
      <header className="App-header">
        <img src={logo} className="App-logo" alt="logo" style={{marginRight:'auto'}} />
        <p style={{marginRight:'20px'}}>
          Authenticated? {authenticated?'Yeah!':'Nope'}
        </p>
      </header>
        {rawData?(
          <div style={{display:'flex', flexDirection:'row'}}>
            <div style={{display:'flex', flexDirection:'column',margin:'10 px auto'}}>
              {rawData.initialFollowers.value.map((k,v)=>(
                <p key={v}>
                  {k.full_name} {k.username}
                </p>
              ))}
            </div>
            <div style={{display:'flex', flexDirection:'column',margin:'10px auto'}}>
              <h1>Unfollowers</h1>
              {
                Object.entries(rawData.deltas).filter(d=>d[1].key==="UNFOLLOWERS").map((o,i)=>(
                <table key={i} className="bad card">
                  <thead>
                    <tr>
                      <th>
                        {new Date(o[0]).toDateString()}
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {o[1].value.map((o1,i1)=>(
                      <tr key={i1}>
                        <td>
                          {o1.full_name}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                ))
              }
            </div>
            <div style={{display:'flex', flexDirection:'column',margin:'10px auto'}}>
              <h1>New followers</h1>
              {
                Object.entries(rawData.deltas).filter(d=>d[1].key==="ADDED_FOLLOWERS").map((o,i)=>(
                <table key={i} className="good card">
                  <thead>
                    <tr>
                      <th>
                        {new Date(o[0]).toDateString()}
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {o[1].value.map((o1,i1)=>(
                      <tr key={i1}>
                        <td>
                          {o1.full_name}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                ))
              }
            </div>
          </div>
        ):(<p>Loading...</p>)}
    </div>
  );
}

export default App;
